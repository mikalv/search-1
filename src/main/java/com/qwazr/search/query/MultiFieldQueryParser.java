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
package com.qwazr.search.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.qwazr.search.index.FieldMap;
import com.qwazr.search.index.QueryContext;
import com.qwazr.search.query.lucene.MultiFieldQueryParserFix;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.Objects;

public class MultiFieldQueryParser extends AbstractClassicQueryParser {

	@JsonCreator
	private MultiFieldQueryParser() {
	}

	public MultiFieldQueryParser(Builder builder) {
		super(builder);
	}

	@Override
	final public Query getQuery(final QueryContext queryContext) throws IOException, ParseException {
		final FieldMap fieldMap = queryContext.getFieldMap();
		final org.apache.lucene.queryparser.classic.MultiFieldQueryParser parser = new MultiFieldQueryParserFix(
				resolveFields(fieldMap), resolveAnalyzer(queryContext), resolvedBoosts(fieldMap));
		setParserParameters(parser);
		return parser.parse(Objects.requireNonNull(queryString, "The query string is missing"));
	}

	public static Builder of() {
		return new Builder();
	}

	public static class Builder extends AbstractParserBuilder<MultiFieldQueryParser> {

		@Override
		public MultiFieldQueryParser build() {
			return new MultiFieldQueryParser(this);
		}

	}
}
