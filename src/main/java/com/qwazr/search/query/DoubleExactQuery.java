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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.qwazr.search.index.QueryContext;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.search.Query;

import java.io.IOException;

public class DoubleExactQuery extends AbstractExactQuery<Double, DoubleExactQuery> {

	@JsonCreator
	public DoubleExactQuery(@JsonProperty("generic_field") final String genericField,
			@JsonProperty("field") final String field, @JsonProperty("value") final Double value) {
		super(DoubleExactQuery.class, genericField, field, value == null ? 0 : value);
	}

	public DoubleExactQuery(final String field, final Double value) {
		this(null, field, value);
	}

	@Override
	public Query getQuery(final QueryContext queryContext) throws IOException {
		return DoublePoint.newExactQuery(resolveField(queryContext.getFieldMap()), value);
	}
}
