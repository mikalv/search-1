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
 */
package com.qwazr.search.analysis;

import com.qwazr.classloader.ClassLoaderManager;
import com.qwazr.search.field.FieldDefinition;
import com.qwazr.utils.ClassLoaderUtils;
import com.qwazr.utils.StringUtils;
import com.qwazr.utils.server.ServerException;
import org.apache.lucene.analysis.Analyzer;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AnalyzerContext {

	public final Map<String, Analyzer> indexAnalyzerMap;
	public final Map<String, Analyzer> queryAnalyzerMap;

	public AnalyzerContext(final Map<String, AnalyzerDefinition> analyzerMap, final Map<String, FieldDefinition> fields)
			throws ServerException {

		if (fields == null || fields.size() == 0) {
			this.indexAnalyzerMap = Collections.emptyMap();
			this.queryAnalyzerMap = Collections.emptyMap();
			return;
		}

		this.indexAnalyzerMap = new HashMap<>();
		this.queryAnalyzerMap = new HashMap<>();

		fields.forEach((fieldName, fieldDef) -> {
			try {

				final Analyzer indexAnalyzer =
						StringUtils.isEmpty(fieldDef.analyzer) ? null : findAnalyzer(analyzerMap, fieldDef.analyzer);
				if (indexAnalyzer != null)
					indexAnalyzerMap.put(fieldName, indexAnalyzer);

				final Analyzer queryAnalyzer = StringUtils.isEmpty(fieldDef.query_analyzer) ?
						indexAnalyzer :
						findAnalyzer(analyzerMap, fieldDef.query_analyzer);
				if (queryAnalyzer != null)
					queryAnalyzerMap.put(fieldName, queryAnalyzer);

			} catch (ReflectiveOperationException | InterruptedException | IOException e) {
				throw new ServerException(Response.Status.NOT_ACCEPTABLE,
						"Class " + fieldDef.analyzer + " not known for the field " + fieldName, e);
			}
		});
	}

	final static String[] analyzerClassPrefixes = { "", "org.apache.lucene.analysis." };

	private static Analyzer findAnalyzer(final Map<String, AnalyzerDefinition> analyzerMap, final String analyzer)
			throws InterruptedException, ReflectiveOperationException, IOException {
		if (analyzerMap != null) {
			AnalyzerDefinition analyzerDef = analyzerMap.get(analyzer);
			if (analyzerDef != null)
				return new CustomAnalyzer(analyzerDef);
		}
		return (Analyzer) ClassLoaderUtils.findClass(ClassLoaderManager.classLoader, analyzer, analyzerClassPrefixes)
				.newInstance();
	}

}
