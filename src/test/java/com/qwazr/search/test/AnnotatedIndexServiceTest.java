/*
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.test;

import com.qwazr.search.analysis.AnalyzerFactory;
import com.qwazr.search.annotations.AnnotatedIndexService;
import com.qwazr.search.annotations.Copy;
import com.qwazr.search.annotations.Index;
import com.qwazr.search.annotations.IndexField;
import com.qwazr.search.field.FieldDefinition;
import com.qwazr.search.index.ExplainDefinition;
import com.qwazr.search.index.IndexManager;
import com.qwazr.search.index.IndexStatus;
import com.qwazr.search.index.QueryBuilder;
import com.qwazr.search.index.QueryDefinition;
import com.qwazr.search.index.ResultDefinition;
import com.qwazr.search.index.SchemaSettingsDefinition;
import com.qwazr.search.query.MatchAllDocsQuery;
import com.qwazr.search.query.MultiFieldQuery;
import com.qwazr.search.query.QueryParserOperator;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AnnotatedIndexServiceTest {

	private static IndexManager indexManager;
	private static ExecutorService executor;
	private static Path workDirectory;
	private static AnnotatedIndexService<IndexRecord> service;

	@BeforeClass
	public static void beforeClass() throws Exception {
		executor = Executors.newCachedThreadPool();
		workDirectory = Files.createTempDirectory("MultiFieldQueryTest");
	}

	@AfterClass
	public static void afterClass() throws Exception {
		if (indexManager != null) {
			indexManager.close();
			indexManager = null;
		}
		if (executor != null) {
			executor.shutdown();
			executor = null;
		}
	}

	@Test
	public void test100createService() throws IOException, URISyntaxException {

		// Create the indexManager
		indexManager = new IndexManager(workDirectory, executor);
		Assert.assertNotNull(indexManager);

		// Register a named analyzer
		final AtomicInteger factoryCallCount = new AtomicInteger();
		final AtomicInteger analyzerCallCount = new AtomicInteger();
		final AnalyzerFactory analyzerFactory = resourceLoader -> {
			Assert.assertNotNull(resourceLoader);
			factoryCallCount.incrementAndGet();
			return new AnnotatedRecord.TestAnalyzer(analyzerCallCount);
		};
		indexManager.registerAnalyzerFactory(AnnotatedRecord.INJECTED_ANALYZER_NAME, analyzerFactory);

		// Get the service
		service = indexManager.getService(IndexRecord.class);
		Assert.assertNotNull(service);

		// Create the schema
		SchemaSettingsDefinition schema = service.createUpdateSchema();
		Assert.assertNotNull(schema);

		// Create the index
		IndexStatus index = service.createUpdateIndex();
		Assert.assertNotNull(index);

		Map<String, FieldDefinition> fields = service.createUpdateFields();
		Assert.assertNotNull(fields);
		Assert.assertEquals(3, fields.size());

		// Check analyzer creation
		service.testAnalyzer(AnnotatedRecord.INJECTED_ANALYZER_NAME, "Test");
		Assert.assertEquals(1, factoryCallCount.get());
		Assert.assertEquals(1, analyzerCallCount.get());
	}

	@Test
	public void test200addDocuments() {
		try {
			service.addDocument(new IndexRecord("This is the title", "Few terms in the content"));
			Assert.assertEquals(Long.valueOf(1), service.getIndexStatus().num_docs);
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void test400checkQuery() throws IOException {
		Assert.assertEquals("Test", service.query(context -> {
			Assert.assertNotNull(context.getIndexReader());
			Assert.assertNotNull(context.getIndexSearcher());
			return "Test";
		}));
	}

	@Test
	public void test400checkWrite() throws IOException {
		Assert.assertEquals("Test", service.write(context -> {
			Assert.assertNotNull(context.getIndexWriter());
			return "Test";
		}));
	}

	@Test
	public void test500query() throws IOException, ReflectiveOperationException {

		Map<String, Float> fieldBoosts = new HashMap<>();
		fieldBoosts.put("title", 10F);
		fieldBoosts.put("content", 1F);
		fieldBoosts.put("full", 0.5F);

		Set<String> fieldsDisableGraph = new HashSet<>();
		fieldsDisableGraph.add("title");
		fieldsDisableGraph.add("content");

		MultiFieldQuery multiFieldQuery =
				new MultiFieldQuery(fieldBoosts, fieldsDisableGraph, QueryParserOperator.AND, "Title terms", null);

		QueryBuilder builder = QueryDefinition.of(multiFieldQuery).queryDebug(true);
		ResultDefinition.WithObject<IndexRecord> results = service.searchQuery(builder.build());
		Assert.assertEquals(
				"(+Synonym(title:titl title:title) +Synonym(title:term title:terms))^10.0 (+Synonym(content:titl content:title) +Synonym(content:term content:terms)) (+Synonym(full:titl full:title) +Synonym(full:term full:terms))^0.5",
				results.query);
		Assert.assertEquals(Long.valueOf(1), results.total_hits);
	}

	@Test
	public void test501query() throws IOException, InterruptedException {
		Long result = service.query(context -> {
			ResultDefinition.WithObject<IndexRecord> res =
					context.searchObject(QueryDefinition.of(new MatchAllDocsQuery()).build(), IndexRecord.class);
			return res.total_hits + context.getIndexReader().numDocs();
		});
		Assert.assertEquals(Long.valueOf(2), result);
	}

	@Test
	public void test510explain() {
		MultiFieldQuery mfq =
				new MultiFieldQuery(QueryParserOperator.AND, "Title terms", null).field("title", 10F, false)
						.field("content", 1.0F, false)
						.field("full", 0.5f, true);
		QueryDefinition query = QueryDefinition.of(mfq).build();
		ResultDefinition.WithObject<IndexRecord> results = service.searchQuery(query);
		Assert.assertNotNull(results);
		int docId = results.getDocuments().get(0).getDoc();
		ExplainDefinition explain = service.explainQuery(query, docId);
		Assert.assertNotNull(explain);
		String explainText = service.explainQueryText(query, docId);
		Assert.assertNotNull(explainText);
		String explainDot = service.explainQueryDot(query, docId, 30);
		Assert.assertNotNull(explainDot);
	}

	@Test
	public void test900close() {
		indexManager.close();
		indexManager = null;
	}

	@Index(schema = "schemaName", name = "indexName")
	public static class IndexRecord {

		@IndexField(template = FieldDefinition.Template.TextField, analyzerClass = MyAnalyzer.class, stored = true)
		@Copy(to = @Copy.To(order = 1, field = "full"))
		final public String title;

		@IndexField(template = FieldDefinition.Template.TextField, analyzerClass = MyAnalyzer.class, stored = true)
		@Copy(to = @Copy.To(order = 2, field = "full"))
		final public String content;

		@IndexField(template = FieldDefinition.Template.TextField, analyzerClass = MyAnalyzer.class)
		final public String full;

		public IndexRecord(String title, String content) {
			this.title = title;
			this.content = content;
			this.full = null;
		}

		public IndexRecord() {
			this(null, null);
		}
	}

	public static class MyAnalyzer extends Analyzer {

		@Override
		protected TokenStreamComponents createComponents(String fieldName) {
			Tokenizer source = new StandardTokenizer();
			TokenStream filter = new KeywordRepeatFilter(source);
			filter = new SnowballFilter(filter, "English");
			filter = new LowerCaseFilter(filter);
			filter = new RemoveDuplicatesTokenFilter(filter);
			return new TokenStreamComponents(source, filter);
		}

	}
}
