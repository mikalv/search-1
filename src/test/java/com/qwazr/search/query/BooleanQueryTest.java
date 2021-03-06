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
package com.qwazr.search.query;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.function.Consumer;

public class BooleanQueryTest {

	private void check(Integer mmsm, Boolean coord, int size, BooleanQuery.Builder builder) {
		builder.setDisableCoord(coord).setMinimumNumberShouldMatch(mmsm);
		Assert.assertEquals(size, builder.getSize());
		final BooleanQuery booleanQuery = builder.build();
		Assert.assertNotNull(booleanQuery);
		Assert.assertEquals(mmsm, booleanQuery.minimum_number_should_match);
		Assert.assertEquals(coord, booleanQuery.disable_coord);
		if (size == 0)
			Assert.assertTrue(booleanQuery.clauses == null || booleanQuery.clauses.isEmpty());
		else
			Assert.assertEquals(size, booleanQuery.clauses.size());
	}

	@Test
	public void empty() {
		final BooleanQuery.Builder builder = new BooleanQuery.Builder();
		check(null, null, 0, builder);
	}

	@Test
	public void noClause() {
		final Integer mmsm = RandomUtils.nextInt(0, 100);
		final Boolean coord = RandomUtils.nextBoolean();
		final BooleanQuery.Builder builder = new BooleanQuery.Builder();
		check(mmsm, coord, 0, builder);
	}

	void checkClauses(Consumer<BooleanQuery.Builder> consumer) {
		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		final int count = RandomUtils.nextInt(1, 5);
		for (int i = 0; i < count; i++)
			consumer.accept(builder);
		check(null, null, count, builder);
	}

	@Test
	public void someClauses() {
		checkClauses(builder -> builder.addClause(BooleanQuery.Occur.must, new MatchAllDocsQuery()));
	}

	@Test
	public void mustClauses() {
		checkClauses(builder -> builder.must(new MatchAllDocsQuery()));
	}

	@Test
	public void shouldClauses() {
		checkClauses(builder -> builder.should(new MatchAllDocsQuery()));
	}

	@Test
	public void mustNotClauses() {
		checkClauses(builder -> builder.mustNot(new MatchAllDocsQuery()));
	}

	@Test
	public void filterClauses() {
		checkClauses(builder -> builder.filter(new MatchAllDocsQuery()));
	}
}
