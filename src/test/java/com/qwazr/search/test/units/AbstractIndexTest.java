/*
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.test.units;

import com.qwazr.search.annotations.AnnotatedIndexService;
import com.qwazr.search.index.ExplainDefinition;
import com.qwazr.search.index.IndexManager;
import com.qwazr.search.index.QueryDefinition;
import com.qwazr.search.index.ResultDefinition;
import com.qwazr.utils.FileUtils;
import com.qwazr.utils.LoggerUtils;
import org.junit.AfterClass;
import org.junit.Assert;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public abstract class AbstractIndexTest {

	private static Path rootDirectory;
	protected static IndexManager indexManager;
	private static ExecutorService executor;

	static final Logger LOGGER = LoggerUtils.getLogger(AbstractIndexTest.class);

	protected static IndexManager initIndexManager() {
		try {
			executor = Executors.newCachedThreadPool();
			rootDirectory = Files.createTempDirectory("qwazr_index_test");
			return indexManager = new IndexManager(rootDirectory, executor);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected static <T> AnnotatedIndexService<T> initIndexService(Class<T> recordClass)
			throws URISyntaxException, IOException {
		if (indexManager == null)
			initIndexManager();
		final AnnotatedIndexService<T> indexService = indexManager.getService(recordClass);
		indexService.createUpdateSchema();
		indexService.createUpdateIndex();
		indexService.createUpdateFields();
		return indexService;
	}

	<T> ResultDefinition.WithObject<T> checkQuery(AnnotatedIndexService<T> indexService, QueryDefinition queryDef,
			Long hitsExpected, String queryDebug) {
		final ResultDefinition.WithObject<T> result = indexService.searchQuery(queryDef);
		Assert.assertNotNull(result);
		if (result.query != null)
			LOGGER.info(result.query);
		if (hitsExpected != null) {
			Assert.assertEquals(hitsExpected, result.total_hits);
			if (hitsExpected > 0) {
				ExplainDefinition explain = indexService.explainQuery(queryDef, result.documents.get(0).getDoc());
				Assert.assertNotNull(explain);
			}
		}
		if (queryDebug != null)
			Assert.assertEquals(queryDebug, result.getQuery());
		return result;
	}

	<T> ResultDefinition.WithObject<T> checkQuery(AnnotatedIndexService<T> indexService, QueryDefinition queryDef) {
		return checkQuery(indexService, queryDef, 1L, null);
	}

	@AfterClass
	public static void afterClass() throws IOException {
		if (indexManager != null) {
			indexManager.close();
			indexManager = null;
		}
		if (rootDirectory != null)
			FileUtils.deleteDirectoryQuietly(rootDirectory);
		if (executor != null) {
			executor.shutdown();
			executor = null;
		}
	}

	public static abstract class WithIndexRecord<T extends IndexRecord> extends AbstractIndexTest {

		protected AnnotatedIndexService<T> service;

		protected WithIndexRecord(Class<T> indexRecordClass) {
			try {
				service = indexManager.getService(indexRecordClass);
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		public ResultDefinition.WithObject<T> checkQuery(QueryDefinition queryDef, Long hitsExpected,
				String queryDebug) {
			return checkQuery(service, queryDef, hitsExpected, queryDebug);
		}

		public ResultDefinition.WithObject<T> checkQuery(QueryDefinition queryDef) {
			return checkQuery(queryDef, 1L, null);
		}

		public static abstract class WithTaxonomy extends WithIndexRecord<IndexRecord.WithTaxonomy> {

			public static AnnotatedIndexService<IndexRecord.WithTaxonomy> indexService;

			public static void initIndexService() throws IOException, URISyntaxException {
				indexService = AbstractIndexTest.initIndexService(IndexRecord.WithTaxonomy.class);
			}

			protected WithTaxonomy() {
				super(IndexRecord.WithTaxonomy.class);
				indexService = service;
			}

			IndexRecord.WithTaxonomy getNewRecord() {
				return new IndexRecord.WithTaxonomy();
			}

			IndexRecord.WithTaxonomy getNewRecord(String id) {
				return new IndexRecord.WithTaxonomy(id);
			}
		}

		public static abstract class NoTaxonomy extends WithIndexRecord<IndexRecord.NoTaxonomy> {

			public static AnnotatedIndexService<IndexRecord.NoTaxonomy> indexService;

			protected static void initIndexService() throws IOException, URISyntaxException {
				indexService = AbstractIndexTest.initIndexService(IndexRecord.NoTaxonomy.class);
			}

			protected NoTaxonomy() {
				super(IndexRecord.NoTaxonomy.class);
				indexService = service;
			}

			IndexRecord.NoTaxonomy getNewRecord() {
				return new IndexRecord.NoTaxonomy();
			}

			IndexRecord.NoTaxonomy getNewRecord(String id) {
				return new IndexRecord.NoTaxonomy(id);
			}
		}
	}
}
