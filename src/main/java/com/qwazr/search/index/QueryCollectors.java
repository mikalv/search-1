/**
 * Copyright 2015-2016 Emmanuel Keller / QWAZR
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
 **/

package com.qwazr.search.index;

import com.qwazr.classloader.ClassLoaderManager;
import com.qwazr.search.field.FieldTypeInterface;
import com.qwazr.utils.server.ServerException;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.search.*;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;

class QueryCollectors {

	final List<Collector> collectors;

	final FacetsCollector facetsCollector;

	final Collection<FunctionCollector> functionsCollectors;

	final Collection<Collector> extCollectors;

	final TotalHitCountCollector totalHitCountCollector;

	final TopDocsCollector topDocsCollector;

	final Collector finalCollector;

	QueryCollectors(boolean bNeedScore, Sort sort, int numHits, final LinkedHashMap<String, FacetDefinition> facets,
			final Collection<QueryDefinition.Function> functions,
			final Collection<QueryDefinition.Collector> externalCollectors, final FieldMap fieldMap)
			throws ReflectiveOperationException, IOException {
		collectors = new ArrayList<>();
		facetsCollector = buildFacetsCollector(facets);
		functionsCollectors = buildFunctionsCollectors(fieldMap, functions);
		totalHitCountCollector = buildTotalHitsCollector(numHits);
		topDocsCollector = buildTopDocCollector(sort, numHits, bNeedScore);
		extCollectors = buildExternalCollectors(externalCollectors);
		finalCollector = getFinalCollector();
	}

	private final <T extends Collector> T add(T collector) {
		collectors.add(collector);
		return collector;
	}

	private final Collector getFinalCollector() {
		switch (collectors.size()) {
		case 0:
			return null;
		case 1:
			return collectors.get(0);
		default:
			return MultiCollector.wrap(collectors);
		}
	}

	private final FacetsCollector buildFacetsCollector(LinkedHashMap<String, FacetDefinition> facets) {
		if (facets == null || facets.isEmpty())
			return null;
		for (FacetDefinition facet : facets.values())
			if (facet.queries == null || facet.queries.isEmpty())
				return add(new FacetsCollector());
		return null;
	}

	private final Collection<FunctionCollector> buildFunctionsCollectors(final FieldMap fieldMap,
			Collection<QueryDefinition.Function> functions) throws ServerException {
		if (functions == null || functions.isEmpty())
			return null;
		Collection<FunctionCollector> functionsCollectors = new ArrayList<FunctionCollector>();
		for (QueryDefinition.Function function : functions) {
			FieldTypeInterface fieldType = fieldMap.getFieldType(function.field);
			if (fieldType == null)
				throw new ServerException(Response.Status.NOT_ACCEPTABLE,
						"Cannot compute the function " + function.function + " because the field is unknown: "
								+ function.field);
			functionsCollectors.add(new FunctionCollector(function, fieldType));
		}
		collectors.addAll(functionsCollectors);
		return functionsCollectors;
	}

	final private Collection<Collector> buildExternalCollectors(final Collection<QueryDefinition.Collector> collectors)
			throws ReflectiveOperationException {
		if (collectors == null || collectors.isEmpty())
			return null;
		final LinkedHashMap<Class<? extends Collector>, Collector> externalCollectors = new LinkedHashMap<>();
		for (QueryDefinition.Collector collector : collectors) {
			final Class<? extends Collector> collectorClass = ClassLoaderManager.findClass(collector.classname);
			if (externalCollectors.containsKey(collectorClass))
				continue;
			final Collector luceneCollector = collectorClass.newInstance();
			externalCollectors.put(collectorClass, luceneCollector);
			add(luceneCollector);
		}
		return externalCollectors.values();
	}

	private final TopDocsCollector buildTopDocCollector(Sort sort, int numHits, boolean bNeedScore) throws IOException {
		if (numHits == 0)
			return null;
		final TopDocsCollector topDocsCollector;
		if (sort != null)
			topDocsCollector = TopFieldCollector.create(sort, numHits, true, bNeedScore, bNeedScore);
		else
			topDocsCollector = TopScoreDocCollector.create(numHits);
		return add(topDocsCollector);
	}

	private final TotalHitCountCollector buildTotalHitsCollector(int numHits) {
		if (numHits > 0)
			return null;
		return add(new TotalHitCountCollector());
	}

	final Integer getTotalHits() {
		if (totalHitCountCollector != null)
			return totalHitCountCollector.getTotalHits();
		if (topDocsCollector != null)
			return topDocsCollector.getTotalHits();
		return null;
	}

	final TopDocs getTopDocs() {
		return topDocsCollector == null ? null : topDocsCollector.topDocs();
	}

}
