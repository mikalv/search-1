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
package com.qwazr.search.index;

import com.qwazr.search.analysis.AnalyzerFactory;
import com.qwazr.server.ServerException;
import com.qwazr.utils.IOUtils;
import com.qwazr.utils.reflection.ConstructorParametersImpl;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

class MultiSearchInstance implements Closeable {

	private final IndexInstance.Provider indexProvider;
	private final ConstructorParametersImpl instanceFactory;
	private final Map<String, AnalyzerFactory> analyzerFactoryMap;
	private final ExecutorService executorService;
	private final Set<IndexInstance> indexInstances;
	private volatile MultiSearchContext multiSearchContext;

	MultiSearchInstance(final IndexInstance.Provider indexProvider, final ConstructorParametersImpl instanceFactory,
			final Map<String, AnalyzerFactory> analyzerFactoryMap, final ExecutorService executorService)
			throws IOException, ServerException {
		this.indexProvider = indexProvider;
		this.instanceFactory = instanceFactory;
		this.analyzerFactoryMap = analyzerFactoryMap;
		this.executorService = executorService;
		this.indexInstances = new HashSet<>();
		this.multiSearchContext = null;
	}

	void register(IndexInstance indexInstance) {
		synchronized (indexInstances) {
			if (!indexInstances.add(indexInstance))
				return;
			multiSearchContext = null;
		}
	}

	void unregister(IndexInstance indexInstance) {
		synchronized (indexInstances) {
			if (!indexInstances.remove(indexInstance))
				return;
			multiSearchContext = null;
		}
	}

	private MultiSearchContext getContext() throws IOException {
		final MultiSearchContext context = multiSearchContext;
		if (context != null)
			return context;
		synchronized (indexInstances) {
			if (multiSearchContext == null)
				multiSearchContext =
						new MultiSearchContext(indexProvider, instanceFactory, analyzerFactoryMap, executorService,
								indexInstances, true);
			return multiSearchContext;
		}
	}

	void refresh() {
		synchronized (indexInstances) {
			if (multiSearchContext != null)
				IOUtils.close(multiSearchContext);
			multiSearchContext = null;
		}
	}

	<T extends ResultDocumentAbstract> ResultDefinition<T> search(final QueryDefinition queryDef,
			final ResultDocuments<T> resultDocuments) throws Exception {
		return getContext().search(queryDef, resultDocuments);
	}

	@Override
	public void close() throws IOException {
		IOUtils.close(multiSearchContext);
	}
}
