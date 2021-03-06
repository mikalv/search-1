/*
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

package com.qwazr.search.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.qwazr.search.index.BytesRefUtils;
import com.qwazr.search.index.FieldMap;
import org.apache.lucene.index.Term;

import java.util.Objects;

public abstract class AbstractFieldQuery<T extends AbstractFieldQuery> extends AbstractQuery<T> {

	@JsonProperty("generic_field")
	final public String genericField;

	final public String field;

	protected AbstractFieldQuery(final Class<T> queryClass, final String genericField, final String field) {
		super(queryClass);
		this.genericField = genericField;
		this.field = Objects.requireNonNull(field, "The field is null");
	}

	protected AbstractFieldQuery(final Class<T> queryClass, final AbstractFieldBuilder builder) {
		this(queryClass, builder.genericField, builder.field);
	}

	static String resolveField(final FieldMap fieldMap, final String genericFieldName, final String field) {
		return fieldMap == null ? field : fieldMap.resolveQueryFieldName(genericFieldName, field);
	}

	final protected String resolveField(final FieldMap fieldMap) {
		return resolveField(fieldMap, genericField, field);
	}

	static Term getResolvedTerm(final FieldMap fieldMap, final String genericFieldName, final String concreteFieldName,
			final Object value) {
		return fieldMap == null ?
				new Term(concreteFieldName, BytesRefUtils.fromAny(value)) :
				Objects.requireNonNull(fieldMap.getFieldType(genericFieldName, concreteFieldName),
						"Unknown field: " + concreteFieldName).term(concreteFieldName, value);
	}

	final protected Term getResolvedTerm(final FieldMap fieldMap, final Object value) {
		return getResolvedTerm(fieldMap, genericField, field, value);
	}

	@Override
	@JsonIgnore
	protected boolean isEqual(T q) {
		return Objects.equals(genericField, q.genericField) && Objects.equals(field, q.field);
	}

	public static abstract class AbstractFieldBuilder {

		final public String genericField;
		final public String field;

		protected AbstractFieldBuilder(final String genericField, final String field) {
			this.genericField = genericField;
			this.field = field;
		}
	}
}
