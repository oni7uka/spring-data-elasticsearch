/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.elasticsearch.core;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.springframework.data.elasticsearch.annotations.Document.VersionType.*;
import static org.springframework.data.elasticsearch.annotations.FieldType.*;
import static org.springframework.data.elasticsearch.annotations.FieldType.Integer;
import static org.springframework.data.elasticsearch.core.document.Document.*;
import static org.springframework.data.elasticsearch.utils.IdGenerator.*;
import static org.springframework.data.elasticsearch.utils.IndexBuilder.*;

import java.lang.Double;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.util.Lists;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder.FilterFunctionBuilder;
import org.elasticsearch.index.query.functionscore.GaussDecayFunctionBuilder;
import org.elasticsearch.join.query.ParentIdQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.collapse.CollapseBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.JoinTypeRelation;
import org.springframework.data.elasticsearch.annotations.JoinTypeRelations;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.ScriptedField;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.core.reindex.ReindexRequest;
import org.springframework.data.elasticsearch.core.reindex.ReindexResponse;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.ScriptField;
import org.springframework.data.elasticsearch.core.document.Explanation;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.join.JoinField;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.elasticsearch.core.query.RescorerQuery.ScoreMode;
import org.springframework.data.elasticsearch.junit.jupiter.SpringIntegrationTest;
import org.springframework.data.elasticsearch.utils.IndexNameProvider;
import org.springframework.data.util.StreamUtils;
import org.springframework.lang.Nullable;

/**
 * Base for testing rest/transport templates. Contains the test common to both implementing classes.
 *
 * @author Rizwan Idrees
 * @author Mohsin Husen
 * @author Franck Marchand
 * @author Abdul Mohammed
 * @author Kevin Leturc
 * @author Mason Chan
 * @author Chris White
 * @author Ilkang Na
 * @author Alen Turkovic
 * @author Sascha Woo
 * @author Jean-Baptiste Nizet
 * @author Zetang Zeng
 * @author Peter Nowak
 * @author Ivan Greene
 * @author Dmitriy Yakovlev
 * @author Peter-Josef Meisch
 * @author Martin Choraine
 * @author Farid Azaza
 * @author Gyula Attila Csorogi
 * @author Roman Puchkovskiy
 * @author Subhobrata Dey
 * @author Farid Faoudi
 * @author Peer Mueller
 * @author Sijia Liu
 */
@SpringIntegrationTest
public abstract class ElasticsearchTemplateTests {

	private static final String INDEX_1_NAME = "test-index-1";
	private static final String INDEX_2_NAME = "test-index-2";
	private static final String INDEX_3_NAME = "test-index-3";

	@Autowired protected ElasticsearchOperations operations;
	private IndexOperations indexOperations;

	@Autowired protected IndexNameProvider indexNameProvider;

	@BeforeEach
	public void before() {

		indexNameProvider.increment();
		indexOperations = operations.indexOps(SampleEntity.class);
		indexOperations.createWithMapping();
	}

	@Test
	@Order(java.lang.Integer.MAX_VALUE)
	void cleanup() {
		operations.indexOps(IndexCoordinates.of(indexNameProvider.getPrefix() + "*")).delete();
	}

	@Test // DATAES-106
	public void shouldReturnCountForGivenCriteriaQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());

		// when

		long count = operations.count(criteriaQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(count).isEqualTo(1);
	}

	@Test
	public void shouldReturnCountForGivenSearchQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		;
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when

		long count = operations.count(searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		// then
		assertThat(count).isEqualTo(1);
	}

	@Test // DATAES-722
	public void shouldReturnObjectForGivenId() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();
		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		SampleEntity sampleEntity1 = operations.get(documentId, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(sampleEntity1).isEqualTo(sampleEntity);
	}

	@Test // DATAES-52, #1678
	public void shouldReturnObjectsForGivenIdsUsingMultiGet() {

		// given
		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("some message")
				.version(System.currentTimeMillis()).build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		NativeSearchQuery query = new NativeSearchQueryBuilder().withIds(Arrays.asList(documentId, documentId2)).build();
		List<MultiGetItem<SampleEntity>> sampleEntities = operations.multiGet(query, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(sampleEntities).hasSize(2);
		assertThat(sampleEntities.get(0).getItem()).isEqualTo(sampleEntity1);
		assertThat(sampleEntities.get(1).getItem()).isEqualTo(sampleEntity2);
	}

	@Test // DATAES-791, #1678
	public void shouldReturnNullObjectForNotExistingIdUsingMultiGet() {

		// given
		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("some message")
				.version(System.currentTimeMillis()).build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		List<String> idsToSearch = Arrays.asList(documentId, nextIdAsString(), documentId2);
		assertThat(idsToSearch).hasSize(3);

		NativeSearchQuery query = new NativeSearchQueryBuilder().withIds(idsToSearch).build();
		List<MultiGetItem<SampleEntity>> sampleEntities = operations.multiGet(query, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(sampleEntities).hasSize(3);
		assertThat(sampleEntities.get(0).getItem()).isEqualTo(sampleEntity1);
		assertThat(sampleEntities.get(1).getItem()).isNull();
		assertThat(sampleEntities.get(2).getItem()).isEqualTo(sampleEntity2);
	}

	@Test // #1678
	@DisplayName("should return failure in multiget result")
	void shouldReturnFailureInMultigetResult() {

		NativeSearchQuery query = new NativeSearchQueryBuilder().withIds(Arrays.asList("42")).build();
		List<MultiGetItem<SampleEntity>> sampleEntities = operations.multiGet(query, SampleEntity.class,
				IndexCoordinates.of("not-existing-index"));

		// then
		assertThat(sampleEntities).hasSize(1);
		assertThat(sampleEntities.get(0).isFailed()).isTrue();
		assertThat(sampleEntities.get(0).getFailure()).isNotNull();
	}

	@Test // DATAES-52, #1678
	public void shouldReturnObjectsForGivenIdsUsingMultiGetWithFields() {

		// given
		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("some message").type("type1")
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("some message").type("type2")
				.version(System.currentTimeMillis()).build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		NativeSearchQuery query = new NativeSearchQueryBuilder().withIds(Arrays.asList(documentId, documentId2))
				.withFields("message", "type").build();
		List<MultiGetItem<SampleEntity>> sampleEntities = operations.multiGet(query, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(sampleEntities).hasSize(2);
	}

	@Test
	public void shouldReturnSearchHitsForGivenSearchQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		assertThat(searchHits.getTotalHitsRelation()).isEqualByComparingTo(TotalHitsRelation.EQUAL_TO);
	}

	@Test // DATAES-595
	public void shouldReturnSearchHitsUsingLocalPreferenceForGivenSearchQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQueryWithValidPreference = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withPreference("_local").build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQueryWithValidPreference, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getTotalHits()).isGreaterThanOrEqualTo(1);
	}

	@Test // DATAES-595
	public void shouldThrowExceptionWhenInvalidPreferenceForSearchQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQueryWithInvalidPreference = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withPreference("_only_nodes:oops").build();

		// when
		assertThatThrownBy(() -> operations.search(searchQueryWithInvalidPreference, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()))).isInstanceOf(Exception.class);
	}

	@Test // DATAES-422 - Add support for IndicesOptions in search queries
	public void shouldPassIndicesOptionsForGivenSearchQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery idxQuery = new IndexQueryBuilder().withId(sampleEntity.getId()).withObject(sampleEntity).build();

		operations.index(idxQuery, IndexCoordinates.of(INDEX_1_NAME));
		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME)).refresh();

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN).build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(INDEX_1_NAME, INDEX_2_NAME));

		// then
		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getTotalHits()).isGreaterThanOrEqualTo(1);
	}

	@Test
	public void shouldDoBulkIndex() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();

		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("some message")
				.version(System.currentTimeMillis()).build();

		indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2));
		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(2);
	}

	@Test
	public void shouldDoBulkUpdate() {

		// given
		String documentId = nextIdAsString();
		String messageBeforeUpdate = "some test message";
		String messageAfterUpdate = "test message";

		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message(messageBeforeUpdate)
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		org.springframework.data.elasticsearch.core.document.Document document = org.springframework.data.elasticsearch.core.document.Document
				.create();
		document.put("message", messageAfterUpdate);
		UpdateQuery updateQuery = UpdateQuery.builder(documentId) //
				.withDocument(document) //
				.build();

		List<UpdateQuery> queries = new ArrayList<>();
		queries.add(updateQuery);

		// when
		operations.bulkUpdate(queries, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		SampleEntity indexedEntity = operations.get(documentId, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(indexedEntity.getMessage()).isEqualTo(messageAfterUpdate);
	}

	@Test
	public void shouldDeleteDocumentForGivenId() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		operations.delete(documentId, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(termQuery("id", documentId)).build();
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(searchHits.getTotalHits()).isEqualTo(0);
	}

	@Test
	public void shouldDeleteEntityForGivenId() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		operations.delete(documentId, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(termQuery("id", documentId)).build();
		SearchHits<SampleEntity> sampleEntities = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(sampleEntities.getTotalHits()).isEqualTo(0);
	}

	@Test
	public void shouldDeleteDocumentForGivenQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		Query query = new NativeSearchQueryBuilder().withQuery(termQuery("id", documentId)).build();
		operations.delete(query, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(termQuery("id", documentId)).build();
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(searchHits.getTotalHits()).isEqualTo(0);
	}

	@Test // DATAES-547
	public void shouldDeleteAcrossIndex() {

		// given
		SampleEntity sampleEntity = SampleEntity.builder() //
				.message("foo") //
				.version(System.currentTimeMillis()) //
				.build();

		IndexQuery idxQuery1 = new IndexQueryBuilder().withId(nextIdAsString()).withObject(sampleEntity).build();

		operations.index(idxQuery1, IndexCoordinates.of(INDEX_1_NAME));
		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME)).refresh();

		IndexQuery idxQuery2 = new IndexQueryBuilder().withId(nextIdAsString()).withObject(sampleEntity).build();

		operations.index(idxQuery2, IndexCoordinates.of(INDEX_2_NAME));
		operations.indexOps(IndexCoordinates.of(INDEX_2_NAME)).refresh();

		// when
		Query query = new NativeSearchQueryBuilder().withQuery(termQuery("message", "foo")).build();
		operations.delete(query, SampleEntity.class, IndexCoordinates.of("test-index-*"));

		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME)).refresh();
		operations.indexOps(IndexCoordinates.of(INDEX_2_NAME)).refresh();

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(termQuery("message", "foo")).build();

		assertThat(operations.count(searchQuery, IndexCoordinates.of(INDEX_1_NAME, INDEX_2_NAME))).isEqualTo(0);
	}

	@Test // DATAES-547
	public void shouldDeleteAcrossIndexWhenNoMatchingDataPresent() {

		// given
		SampleEntity sampleEntity = SampleEntity.builder() //
				.message("positive") //
				.version(System.currentTimeMillis()) //
				.build();

		IndexQuery idxQuery1 = new IndexQueryBuilder().withId(nextIdAsString()).withObject(sampleEntity).build();

		operations.index(idxQuery1, IndexCoordinates.of(INDEX_1_NAME));
		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME)).refresh();

		IndexQuery idxQuery2 = new IndexQueryBuilder().withId(nextIdAsString()).withObject(sampleEntity).build();

		operations.index(idxQuery2, IndexCoordinates.of(INDEX_2_NAME));
		operations.indexOps(IndexCoordinates.of(INDEX_2_NAME)).refresh();

		// when
		Query query = new NativeSearchQueryBuilder().withQuery(termQuery("message", "negative")).build();

		operations.delete(query, SampleEntity.class, IndexCoordinates.of("test-index-*"));

		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME)).refresh();
		operations.indexOps(IndexCoordinates.of(INDEX_2_NAME)).refresh();

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(termQuery("message", "positive")).build();

		assertThat(operations.count(searchQuery, IndexCoordinates.of("test-index-*"))).isEqualTo(2);
	}

	@Test
	public void shouldFilterSearchResultsForGivenFilter() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withFilter(boolQuery().filter(termQuery("id", documentId))).build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
	}

	@Test
	public void shouldSortResultsGivenSortCriteria() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();
		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("abc").rate(10)
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("xyz").rate(5)
				.version(System.currentTimeMillis()).build();

		// third document
		String documentId3 = nextIdAsString();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(documentId3).message("xyz").rate(15)
				.version(System.currentTimeMillis()).build();

		indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2, sampleEntity3));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withSort(new FieldSortBuilder("rate").order(SortOrder.ASC)).build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(3);
		assertThat(searchHits.getSearchHit(0).getContent().getRate()).isEqualTo(sampleEntity2.getRate());
	}

	@Test
	public void shouldSortResultsGivenMultipleSortCriteria() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();
		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("abc").rate(10)
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("xyz").rate(5)
				.version(System.currentTimeMillis()).build();

		// third document
		String documentId3 = nextIdAsString();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(documentId3).message("xyz").rate(15)
				.version(System.currentTimeMillis()).build();

		indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2, sampleEntity3));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()) //
				.withSorts( //
						new FieldSortBuilder("rate").order(SortOrder.ASC), //
						new FieldSortBuilder("message").order(SortOrder.ASC)) //
				.build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(3);
		assertThat(searchHits.getSearchHit(0).getContent().getRate()).isEqualTo(sampleEntity2.getRate());
		assertThat(searchHits.getSearchHit(1).getContent().getMessage()).isEqualTo(sampleEntity1.getMessage());
	}

	@Test // DATAES-312
	public void shouldSortResultsGivenNullFirstSortCriteria() {

		// given
		List<IndexQuery> indexQueries;

		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("abc").rate(15)
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("xyz").rate(5)
				.version(System.currentTimeMillis()).build();

		// third document
		String documentId3 = nextIdAsString();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(documentId3).rate(10).version(System.currentTimeMillis())
				.build();

		indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2, sampleEntity3));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		Query searchQuery = operations.matchAllQuery();
		searchQuery.setPageable(PageRequest.of(0, 10, Sort.by(Sort.Order.asc("message").nullsFirst())));

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(3);
		assertThat(searchHits.getSearchHit(0).getContent().getRate()).isEqualTo(sampleEntity3.getRate());
		assertThat(searchHits.getSearchHit(1).getContent().getMessage()).isEqualTo(sampleEntity1.getMessage());
	}

	@Test // DATAES-312
	public void shouldSortResultsGivenNullLastSortCriteria() {

		// given
		List<IndexQuery> indexQueries;

		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("abc").rate(15)
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("xyz").rate(5)
				.version(System.currentTimeMillis()).build();

		// third document
		String documentId3 = nextIdAsString();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(documentId3).rate(10).version(System.currentTimeMillis())
				.build();

		indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2, sampleEntity3));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		Query searchQuery = operations.matchAllQuery();
		searchQuery.setPageable(PageRequest.of(0, 10, Sort.by(Sort.Order.asc("message").nullsLast())));

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(3);
		assertThat(searchHits.getSearchHit(0).getContent().getRate()).isEqualTo(sampleEntity1.getRate());
		assertThat(searchHits.getSearchHit(1).getContent().getMessage()).isEqualTo(sampleEntity2.getMessage());
	}

	@Test // DATAES-467, DATAES-657
	public void shouldSortResultsByScore() {

		// given
		List<SampleEntity> entities = Arrays.asList( //
				SampleEntity.builder().id("1").message("green").build(), //
				SampleEntity.builder().id("2").message("yellow green").build(), //
				SampleEntity.builder().id("3").message("blue").build());

		operations.bulkIndex(getIndexQueries(entities), IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder() //
				.withQuery(matchQuery("message", "green")) //
				.withPageable(PageRequest.of(0, 10, Sort.by(Sort.Order.asc("_score")))) //
				.build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(2);
		assertThat(searchHits.getSearchHit(0).getContent().getId()).isEqualTo("2");
		assertThat(searchHits.getSearchHit(1).getContent().getId()).isEqualTo("1");
	}

	@Test
	public void shouldExecuteStringQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		StringQuery stringQuery = new StringQuery(matchAllQuery().toString());

		// when
		SearchHits<SampleEntity> searchHits = operations.search(stringQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
	}

	@Test
	public void shouldUseScriptedFields() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = new SampleEntity();
		sampleEntity.setId(documentId);
		sampleEntity.setRate(2);
		sampleEntity.setMessage("some message");
		sampleEntity.setVersion(System.currentTimeMillis());

		IndexQuery indexQuery = new IndexQuery();
		indexQuery.setId(documentId);
		indexQuery.setObject(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		Map<String, Object> params = new HashMap<>();
		params.put("factor", 2);

		// when
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).withScriptField(
				new ScriptField("scriptedRate", new Script(ScriptType.INLINE, "expression", "doc['rate'] * factor", params)))
				.build();
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		assertThat(searchHits.getSearchHit(0).getContent().getScriptedRate()).isEqualTo(4.0);
	}

	@Test
	public void shouldReturnPageableResultsGivenStringQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		StringQuery stringQuery = new StringQuery(matchAllQuery().toString(), PageRequest.of(0, 10));

		// when
		SearchHits<SampleEntity> searchHits = operations.search(stringQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isGreaterThanOrEqualTo(1);
	}

	@Test
	public void shouldReturnSortedResultsGivenStringQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = new SampleEntity();
		sampleEntity.setId(documentId);
		sampleEntity.setMessage("some message");
		sampleEntity.setVersion(System.currentTimeMillis());

		IndexQuery indexQuery = new IndexQuery();
		indexQuery.setId(documentId);
		indexQuery.setObject(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		StringQuery stringQuery = new StringQuery(matchAllQuery().toString(), PageRequest.of(0, 10),
				Sort.by(Sort.Order.asc("message")));

		// when
		SearchHits<SampleEntity> searchHits = operations.search(stringQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isGreaterThanOrEqualTo(1);
	}

	@Test
	public void shouldReturnObjectMatchingGivenStringQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		StringQuery stringQuery = new StringQuery(termQuery("id", documentId).toString());

		// when
		SearchHit<SampleEntity> sampleEntity1 = operations.searchOne(stringQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(sampleEntity1).isNotNull();
		assertThat(sampleEntity1.getContent().getId()).isEqualTo(documentId);
	}

	@Test
	public void shouldCreateIndexGivenEntityClass() {

		// when
		// creation is done in setup method
		Map setting = indexOperations.getSettings();

		// then
		assertThat(setting.get("index.number_of_shards")).isEqualTo("1");
		assertThat(setting.get("index.number_of_replicas")).isEqualTo("0");
	}

	@Test
	public void shouldExecuteGivenCriteriaQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("test message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria("message").contains("test"));

		// when
		SearchHit<SampleEntity> sampleEntity1 = operations.searchOne(criteriaQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(sampleEntity1).isNotNull();
	}

	@Test
	public void shouldDeleteGivenCriteriaQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("test message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria("message").contains("test"));

		// when
		operations.delete(criteriaQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		StringQuery stringQuery = new StringQuery(matchAllQuery().toString());
		SearchHits<SampleEntity> sampleEntities = operations.search(stringQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(sampleEntities).isEmpty();
	}

	@Test
	public void shouldReturnFieldsBasedOnSourceFilter() {

		// given
		String documentId = nextIdAsString();
		String message = "some test message";
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message(message)
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		FetchSourceFilterBuilder sourceFilter = new FetchSourceFilterBuilder();
		sourceFilter.withIncludes("message");

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withSourceFilter(sourceFilter.build()).build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		assertThat(searchHits.getSearchHit(0).getContent().getMessage()).isEqualTo(message);
	}

	@Test
	public void shouldReturnSimilarResultsGivenMoreLikeThisQuery() {

		// given
		String sampleMessage = "So we build a web site or an application and want to add search to it, "
				+ "and then it hits us: getting search working is hard. We want our search solution to be fast,"
				+ " we want a painless setup and a completely free search schema, we want to be able to index data simply using JSON over HTTP, "
				+ "we want our search server to be always available, we want to be able to start with one machine and scale to hundreds, "
				+ "we want real-time search, we want simple multi-tenancy, and we want a solution that is built for the cloud.";

		String documentId1 = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId1).message(sampleMessage)
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		String documentId2 = nextIdAsString();

		operations.index(
				getIndexQuery(
						SampleEntity.builder().id(documentId2).message(sampleMessage).version(System.currentTimeMillis()).build()),
				IndexCoordinates.of(indexNameProvider.indexName()));

		MoreLikeThisQuery moreLikeThisQuery = new MoreLikeThisQuery();
		moreLikeThisQuery.setId(documentId2);
		moreLikeThisQuery.addFields("message");
		moreLikeThisQuery.setMinDocFreq(1);

		// when
		SearchHits<SampleEntity> searchHits = operations.search(moreLikeThisQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		List<SampleEntity> content = searchHits.getSearchHits().stream().map(SearchHit::getContent)
				.collect(Collectors.toList());
		assertThat(content).contains(sampleEntity);
	}

	@Test // #1787
	@DisplayName("should use Pageable on MoreLikeThis queries")
	void shouldUsePageableOnMoreLikeThisQueries() {

		String sampleMessage = "So we build a web site or an application and want to add search to it, "
				+ "and then it hits us: getting search working is hard. We want our search solution to be fast,"
				+ " we want a painless setup and a completely free search schema, we want to be able to index data simply using JSON over HTTP, "
				+ "we want our search server to be always available, we want to be able to start with one machine and scale to hundreds, "
				+ "we want real-time search, we want simple multi-tenancy, and we want a solution that is built for the cloud.";
		String referenceId = nextIdAsString();
		Collection<String> ids = IntStream.rangeClosed(1, 10).mapToObj(i -> nextIdAsString()).collect(Collectors.toList());
		ids.add(referenceId);
		ids.stream()
				.map(id -> getIndexQuery(
						SampleEntity.builder().id(id).message(sampleMessage).version(System.currentTimeMillis()).build()))
				.forEach(indexQuery -> operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName())));

		MoreLikeThisQuery moreLikeThisQuery = new MoreLikeThisQuery();
		moreLikeThisQuery.setId(referenceId);
		moreLikeThisQuery.addFields("message");
		moreLikeThisQuery.setMinDocFreq(1);
		moreLikeThisQuery.setPageable(PageRequest.of(0, 5));

		SearchHits<SampleEntity> searchHits = operations.search(moreLikeThisQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(searchHits.getTotalHits()).isEqualTo(10);
		assertThat(searchHits.getSearchHits()).hasSize(5);

		Collection<String> returnedIds = searchHits.getSearchHits().stream().map(SearchHit::getId)
				.collect(Collectors.toList());

		moreLikeThisQuery.setPageable(PageRequest.of(1, 5));

		searchHits = operations.search(moreLikeThisQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(searchHits.getTotalHits()).isEqualTo(10);
		assertThat(searchHits.getSearchHits()).hasSize(5);

		searchHits.getSearchHits().stream().map(SearchHit::getId).forEach(returnedIds::add);

		assertThat(returnedIds).hasSize(10);
		assertThat(ids).containsAll(returnedIds);
	}

	@Test // DATAES-167
	public void shouldReturnResultsWithScanAndScrollForGivenCriteriaQuery() {

		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 30);

		// when
		operations.bulkIndex(entities, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());
		criteriaQuery.setPageable(PageRequest.of(0, 10));

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				criteriaQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scroll.getScrollId(), 1000,
					SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scroll.getScrollId());
		assertThat(sampleEntities).hasSize(30);
	}

	@Test
	public void shouldReturnResultsWithScanAndScrollForGivenSearchQuery() {

		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 30);

		// when
		operations.bulkIndex(entities, IndexCoordinates.of(indexNameProvider.indexName()));

		// then

		Query searchQuery = operations.matchAllQuery();
		searchQuery.setPageable(PageRequest.of(0, 10));

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scroll.getScrollId(), 1000,
					SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scroll.getScrollId());
		assertThat(sampleEntities).hasSize(30);
	}

	@Test // DATAES-167
	public void shouldReturnResultsWithScanAndScrollForSpecifiedFieldsForCriteriaQuery() {

		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 30);

		// when
		operations.bulkIndex(entities, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());
		criteriaQuery.addFields("message");
		criteriaQuery.setPageable(PageRequest.of(0, 10));

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				criteriaQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		String scrollId = scroll.getScrollId();
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scrollId = scroll.getScrollId();
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scrollId, 1000, SampleEntity.class,
					IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scrollId);
		assertThat(sampleEntities).hasSize(30);
	}

	@Test // DATAES-84
	public void shouldReturnResultsWithScanAndScrollForSpecifiedFieldsForSearchCriteria() {

		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 30);

		// when
		operations.bulkIndex(entities, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).withFields("message")
				.withQuery(matchAllQuery()).withPageable(PageRequest.of(0, 10)).build();

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		String scrollId = scroll.getScrollId();
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scrollId = scroll.getScrollId();
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scrollId, 1000, SampleEntity.class,
					IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scrollId);
		assertThat(sampleEntities).hasSize(30);
	}

	@Test // DATAES-167
	public void shouldReturnResultsForScanAndScrollWithCustomResultMapperForGivenCriteriaQuery() {

		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 30);

		// when
		operations.bulkIndex(entities, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());
		criteriaQuery.setPageable(PageRequest.of(0, 10));

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				criteriaQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		String scrollId = scroll.getScrollId();
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scrollId = scroll.getScrollId();
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scrollId, 1000, SampleEntity.class,
					IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scrollId);
		assertThat(sampleEntities).hasSize(30);
	}

	@Test
	public void shouldReturnResultsForScanAndScrollWithCustomResultMapperForGivenSearchQuery() {

		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 30);

		// when
		operations.bulkIndex(entities, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withPageable(PageRequest.of(0, 10)).build();

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		String scrollId = scroll.getScrollId();
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scrollId = scroll.getScrollId();
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scrollId, 1000, SampleEntity.class,
					IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scrollId);
		assertThat(sampleEntities).hasSize(30);
	}

	@Test // DATAES-217
	public void shouldReturnResultsWithScanAndScrollForGivenCriteriaQueryAndClass() {

		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 30);

		// when
		operations.bulkIndex(entities, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());
		criteriaQuery.setPageable(PageRequest.of(0, 10));

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				criteriaQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		String scrollId = scroll.getScrollId();
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scrollId = scroll.getScrollId();
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scrollId, 1000, SampleEntity.class,
					IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scrollId);
		assertThat(sampleEntities).hasSize(30);
	}

	@Test // DATAES-217
	public void shouldReturnResultsWithScanAndScrollForGivenSearchQueryAndClass() {

		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 30);

		// when
		operations.bulkIndex(entities, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withPageable(PageRequest.of(0, 10)).build();

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		String scrollId = scroll.getScrollId();
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scrollId = scroll.getScrollId();
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scrollId, 1000, SampleEntity.class,
					IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scrollId);
		assertThat(sampleEntities).hasSize(30);
	}

	@Test // DATAES-167, DATAES-831
	public void shouldReturnAllResultsWithStreamForGivenCriteriaQuery() {

		operations.bulkIndex(createSampleEntitiesWithMessage("Test message", 30),
				IndexCoordinates.of(indexNameProvider.indexName()));

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());
		criteriaQuery.setPageable(PageRequest.of(0, 10));

		long count = StreamUtils.createStreamFromIterator(operations.searchForStream(criteriaQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()))).count();

		assertThat(count).isEqualTo(30);
	}

	@Test // DATAES-831
	void shouldLimitStreamResultToRequestedSize() {

		operations.bulkIndex(createSampleEntitiesWithMessage("Test message", 30),
				IndexCoordinates.of(indexNameProvider.indexName()));

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());
		criteriaQuery.setMaxResults(10);

		long count = StreamUtils.createStreamFromIterator(operations.searchForStream(criteriaQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()))).count();

		assertThat(count).isEqualTo(10);
	}

	private static List<IndexQuery> createSampleEntitiesWithMessage(String message, int numberOfEntities) {
		List<IndexQuery> indexQueries = new ArrayList<>();
		for (int i = 0; i < numberOfEntities; i++) {
			String documentId = UUID.randomUUID().toString();
			SampleEntity sampleEntity = new SampleEntity();
			sampleEntity.setId(documentId);
			sampleEntity.setMessage(message);
			sampleEntity.setRate(2);
			sampleEntity.setVersion(System.currentTimeMillis());
			IndexQuery indexQuery = new IndexQuery();
			indexQuery.setId(documentId);
			indexQuery.setObject(sampleEntity);
			indexQueries.add(indexQuery);
		}
		return indexQueries;
	}

	@Test
	public void shouldReturnListForGivenCriteria() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();
		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("test message")
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("test test").rate(5)
				.version(System.currentTimeMillis()).build();

		// third document
		String documentId3 = nextIdAsString();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(documentId3).message("some message").rate(15)
				.version(System.currentTimeMillis()).build();

		indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2, sampleEntity3));

		// when
		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		CriteriaQuery singleCriteriaQuery = new CriteriaQuery(new Criteria("message").contains("test"));
		CriteriaQuery multipleCriteriaQuery = new CriteriaQuery(
				new Criteria("message").contains("some").and("message").contains("message"));
		SearchHits<SampleEntity> sampleEntitiesForSingleCriteria = operations.search(singleCriteriaQuery,
				SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		SearchHits<SampleEntity> sampleEntitiesForAndCriteria = operations.search(multipleCriteriaQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		// then
		assertThat(sampleEntitiesForSingleCriteria).hasSize(2);
		assertThat(sampleEntitiesForAndCriteria).hasSize(1);
	}

	@Test
	public void shouldReturnListForGivenStringQuery() {

		// given
		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("test message")
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("test test").rate(5)
				.version(System.currentTimeMillis()).build();

		// third document
		String documentId3 = nextIdAsString();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(documentId3).message("some message").rate(15)
				.version(System.currentTimeMillis()).build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2, sampleEntity3));

		// when
		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		StringQuery stringQuery = new StringQuery(matchAllQuery().toString());
		SearchHits<SampleEntity> sampleEntities = operations.search(stringQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(sampleEntities).hasSize(3);
	}

	@Test
	public void shouldPutMappingForGivenEntity() {

		// given
		Class<SampleEntity> entityClass = SampleEntity.class;
		operations.indexOps(entityClass).delete();
		operations.indexOps(entityClass).create();

		// when

		// then
		assertThat(indexOperations.putMapping(entityClass)).isTrue();
	}

	@Test // DATAES-305
	public void shouldPutMappingWithCustomIndexName() {

		// given
		Class<SampleEntity> entity = SampleEntity.class;
		IndexOperations indexOperations1 = operations.indexOps(IndexCoordinates.of(INDEX_1_NAME));
		indexOperations1.delete();
		indexOperations1.create();

		// when
		indexOperations1.putMapping(entity);

		// then
		Map<String, Object> mapping = indexOperations.getMapping();
		assertThat(mapping.get("properties")).isNotNull();
	}

	@Test // DATAES-987
	@DisplayName("should read mappings from alias")
	void shouldReadMappingsFromAlias() {

		String indexName = indexNameProvider.indexName();
		String aliasName = "alias-" + indexName;

		indexOperations.alias( //
				new AliasActions( //
						new AliasAction.Add( //
								AliasActionParameters.builder() //
										.withIndices(indexName) //
										.withAliases(aliasName) //
										.build()) //
				) //
		);

		IndexOperations aliasIndexOps = operations.indexOps(IndexCoordinates.of(aliasName));
		Map<String, Object> mappingFromAlias = aliasIndexOps.getMapping();

		assertThat(mappingFromAlias).isNotNull();
		assertThat(
				((Map<String, Object>) ((Map<String, Object>) mappingFromAlias.get("properties")).get("message")).get("type"))
						.isEqualTo("text");
	}

	@Test
	public void shouldDeleteIndexForGivenEntity() {

		// given
		Class<?> clazz = SampleEntity.class;

		// when
		indexOperations.delete();

		// then
		assertThat(indexOperations.exists()).isFalse();
	}

	@Test
	public void shouldDoPartialUpdateForExistingDocument() {

		// given
		String documentId = nextIdAsString();
		String messageBeforeUpdate = "some test message";
		String messageAfterUpdate = "test message";

		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message(messageBeforeUpdate)
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		org.springframework.data.elasticsearch.core.document.Document document = org.springframework.data.elasticsearch.core.document.Document
				.create();
		document.put("message", messageAfterUpdate);
		UpdateQuery updateQuery = UpdateQuery.builder(documentId)//
				.withDocument(document) //
				.build();

		// when
		operations.update(updateQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		SampleEntity indexedEntity = operations.get(documentId, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(indexedEntity.getMessage()).isEqualTo(messageAfterUpdate);
	}

	@Test
	void shouldDoUpdateByQueryForExistingDocument() {
		// given
		final String documentId = nextIdAsString();
		final String messageBeforeUpdate = "some test message";
		final String messageAfterUpdate = "test message";

		final SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message(messageBeforeUpdate)
				.version(System.currentTimeMillis()).build();

		final IndexQuery indexQuery = getIndexQuery(sampleEntity);

		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		final NativeSearchQuery query = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		final UpdateQuery updateQuery = UpdateQuery.builder(query)
				.withScriptType(org.springframework.data.elasticsearch.core.ScriptType.INLINE)
				.withScript("ctx._source['message'] = params['newMessage']").withLang("painless")
				.withParams(Collections.singletonMap("newMessage", messageAfterUpdate)).withAbortOnVersionConflict(true)
				.build();

		// when
		operations.updateByQuery(updateQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		SampleEntity indexedEntity = operations.get(documentId, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(indexedEntity.getMessage()).isEqualTo(messageAfterUpdate);
	}

	@Test // DATAES-227
	public void shouldUseUpsertOnUpdate() {

		// given
		Map<String, Object> doc = new HashMap<>();
		doc.put("id", "1");
		doc.put("message", "test");

		org.springframework.data.elasticsearch.core.document.Document document = org.springframework.data.elasticsearch.core.document.Document
				.from(doc);

		UpdateQuery updateQuery = UpdateQuery.builder("1") //
				.withDocument(document) //
				.withUpsert(document) //
				.build();

		// when
		UpdateRequest request = getRequestFactory().updateRequest(updateQuery, IndexCoordinates.of("index"));

		// then
		assertThat(request).isNotNull();
		assertThat(request.upsertRequest()).isNotNull();
	}

	@Test // DATAES-693
	public void shouldReturnSourceWhenRequested() {
		// given
		Map<String, Object> doc = new HashMap<>();
		doc.put("id", "1");
		doc.put("message", "test");

		org.springframework.data.elasticsearch.core.document.Document document = org.springframework.data.elasticsearch.core.document.Document
				.from(doc);

		UpdateQuery updateQuery = UpdateQuery.builder("1") //
				.withDocument(document) //
				.withFetchSource(true) //
				.build();

		// when
		UpdateRequest request = getRequestFactory().updateRequest(updateQuery, IndexCoordinates.of("index"));

		// then
		assertThat(request).isNotNull();
		assertThat(request.fetchSource()).isEqualTo(FetchSourceContext.FETCH_SOURCE);
	}

	@Test
	public void shouldDoUpsertIfDocumentDoesNotExist() {

		// given
		String documentId = nextIdAsString();
		org.springframework.data.elasticsearch.core.document.Document document = org.springframework.data.elasticsearch.core.document.Document
				.create();
		document.put("message", "test message");
		UpdateQuery updateQuery = UpdateQuery.builder(documentId) //
				.withDocument(document) //
				.withDocAsUpsert(true) //
				.build();

		// when
		operations.update(updateQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		SampleEntity indexedEntity = operations.get(documentId, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(indexedEntity.getMessage()).isEqualTo("test message");
	}

	@Test // DATAES-671
	public void shouldPassIndicesOptionsForGivenSearchScrollQuery() {

		// given
		long scrollTimeInMillis = 3000;
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery idxQuery = new IndexQueryBuilder().withId(sampleEntity.getId()).withObject(sampleEntity).build();

		IndexCoordinates index = IndexCoordinates.of(INDEX_1_NAME);
		operations.index(idxQuery, index);

		// when
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN).build();

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations)
				.searchScrollStart(scrollTimeInMillis, searchQuery, SampleEntity.class, index);

		List<SearchHit<SampleEntity>> entities = new ArrayList<>(scroll.getSearchHits());

		while (scroll.hasSearchHits()) {
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scroll.getScrollId(),
					scrollTimeInMillis, SampleEntity.class, index);

			entities.addAll(scroll.getSearchHits());
		}

		// then
		assertThat(entities).isNotNull();
		assertThat(entities.size()).isGreaterThanOrEqualTo(1);
	}

	@Test // DATAES-487
	public void shouldReturnSameEntityForMultiSearch() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();

		indexQueries.add(buildIndex(SampleEntity.builder().id("1").message("ab").build()));
		indexQueries.add(buildIndex(SampleEntity.builder().id("2").message("bc").build()));
		indexQueries.add(buildIndex(SampleEntity.builder().id("3").message("ac").build()));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		List<NativeSearchQuery> queries = new ArrayList<>();

		queries.add(new NativeSearchQueryBuilder().withQuery(termQuery("message", "ab")).build());
		queries.add(new NativeSearchQueryBuilder().withQuery(termQuery("message", "bc")).build());
		queries.add(new NativeSearchQueryBuilder().withQuery(termQuery("message", "ac")).build());

		// then
		List<SearchHits<SampleEntity>> searchHits = operations.multiSearch(queries, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		for (SearchHits<SampleEntity> sampleEntity : searchHits) {
			assertThat(sampleEntity.getTotalHits()).isEqualTo(1);
		}
	}

	@Test // DATAES-487
	public void shouldReturnDifferentEntityForMultiSearch() {

		// given
		Class<Book> clazz = Book.class;
		IndexOperations bookIndexOperations = operations.indexOps(Book.class);
		bookIndexOperations.delete();
		bookIndexOperations.create();
		indexOperations.putMapping(clazz);
		bookIndexOperations.refresh();

		IndexCoordinates bookIndex = IndexCoordinates.of("test-index-book-core-template");

		operations.index(buildIndex(SampleEntity.builder().id("1").message("ab").build()),
				IndexCoordinates.of(indexNameProvider.indexName()));
		operations.index(buildIndex(Book.builder().id("2").description("bc").build()), bookIndex);

		bookIndexOperations.refresh();

		// when
		List<NativeSearchQuery> queries = new ArrayList<>();
		queries.add(new NativeSearchQueryBuilder().withQuery(termQuery("message", "ab")).build());
		queries.add(new NativeSearchQueryBuilder().withQuery(termQuery("description", "bc")).build());

		List<SearchHits<?>> searchHitsList = operations.multiSearch(queries, Lists.newArrayList(SampleEntity.class, clazz),
				IndexCoordinates.of(indexNameProvider.indexName(), bookIndex.getIndexName()));

		// then
		SearchHits<?> searchHits0 = searchHitsList.get(0);
		assertThat(searchHits0.getTotalHits()).isEqualTo(1L);
		SearchHit<SampleEntity> searchHit0 = (SearchHit<SampleEntity>) searchHits0.getSearchHit(0);
		assertThat(searchHit0.getContent().getClass()).isEqualTo(SampleEntity.class);
		SearchHits<?> searchHits1 = searchHitsList.get(1);
		assertThat(searchHits1.getTotalHits()).isEqualTo(1L);
		SearchHit<Book> searchHit1 = (SearchHit<Book>) searchHits1.getSearchHit(0);
		assertThat(searchHit1.getContent().getClass()).isEqualTo(clazz);
	}

	@Test
	public void shouldIndexDocumentForSpecifiedSource() {

		// given
		String documentSource = "{\"id\":\"2333343434\",\"type\":null,\"message\":\"some message\",\"rate\":0,\"available\":false,\"highlightedMessage\":null,\"version\":1385208779482}";
		IndexQuery indexQuery = new IndexQuery();
		indexQuery.setId("2333343434");
		indexQuery.setSource(documentSource);

		// when
		IndexCoordinates index = IndexCoordinates.of(indexNameProvider.indexName());
		operations.index(indexQuery, index);

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(termQuery("id", indexQuery.getId()))
				.build();

		// then
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class, index);
		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		assertThat(searchHits.getSearchHit(0).getContent().getId()).isEqualTo(indexQuery.getId());
	}

	@Test
	public void shouldThrowElasticsearchExceptionWhenNoDocumentSpecified() {

		// given
		final IndexQuery indexQuery = new IndexQuery();
		indexQuery.setId("2333343434");

		// when
		assertThatThrownBy(() -> operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName())))
				.isInstanceOf(InvalidDataAccessApiUsageException.class);
	}

	@Test // DATAES-848
	public void shouldReturnIndexName() {
		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 3);
		// when
		String indexName = indexNameProvider.indexName();
		operations.bulkIndex(entities, IndexCoordinates.of(indexName));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(termQuery("message", "message"))
				.withPageable(PageRequest.of(0, 100)).build();
		// then
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class);

		searchHits.forEach(searchHit -> {
			assertThat(searchHit.getIndex()).isEqualTo(indexName);
		});
	}

	@Test
	public void shouldReturnDocumentAboveMinimalScoreGivenQuery() {
		// given
		List<IndexQuery> indexQueries = new ArrayList<>();

		indexQueries.add(buildIndex(SampleEntity.builder().id("1").message("ab").build()));
		indexQueries.add(buildIndex(SampleEntity.builder().id("2").message("bc").build()));
		indexQueries.add(buildIndex(SampleEntity.builder().id("3").message("ac").build()));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(wildcardQuery("message", "*a*")).should(wildcardQuery("message", "*b*")))
				.withMinScore(2.0F).build();

		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		assertThat(searchHits.getSearchHit(0).getContent().getMessage()).isEqualTo("ab");
	}

	@Test // DATAES-462
	public void shouldReturnScores() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();

		indexQueries.add(buildIndex(SampleEntity.builder().id("1").message("ab xz").build()));
		indexQueries.add(buildIndex(SampleEntity.builder().id("2").message("bc").build()));
		indexQueries.add(buildIndex(SampleEntity.builder().id("3").message("ac xz hi").build()));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(termQuery("message", "xz"))
				.withSort(SortBuilders.fieldSort("message")).withTrackScores(true).build();

		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits.getMaxScore()).isGreaterThan(0f);
		assertThat(searchHits.getSearchHit(0).getScore()).isGreaterThan(0f);
	}

	@Test
	public void shouldDoIndexWithoutId() {

		// given
		// document
		SampleEntity sampleEntity = new SampleEntity();
		sampleEntity.setMessage("some message");
		sampleEntity.setVersion(System.currentTimeMillis());

		IndexQuery indexQuery = new IndexQuery();
		indexQuery.setObject(sampleEntity);

		// when
		String documentId = operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(sampleEntity.getId()).isEqualTo(documentId);

		SampleEntity result = operations.get(documentId, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(result.getId()).isEqualTo(documentId);
	}

	@Test
	public void shouldDoBulkIndexWithoutId() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();
		// first document
		SampleEntity sampleEntity1 = new SampleEntity();
		sampleEntity1.setMessage("some message");
		sampleEntity1.setVersion(System.currentTimeMillis());

		IndexQuery indexQuery1 = new IndexQuery();
		indexQuery1.setObject(sampleEntity1);
		indexQueries.add(indexQuery1);

		// second document
		SampleEntity sampleEntity2 = new SampleEntity();
		sampleEntity2.setMessage("some message");
		sampleEntity2.setVersion(System.currentTimeMillis());

		IndexQuery indexQuery2 = new IndexQuery();
		indexQuery2.setObject(sampleEntity2);
		indexQueries.add(indexQuery2);

		// when
		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(searchHits.getTotalHits()).isEqualTo(2);

		assertThat(searchHits.getSearchHit(0).getContent().getId()).isNotNull();
		assertThat(searchHits.getSearchHit(1).getContent().getId()).isNotNull();
	}

	@Test
	public void shouldIndexMapWithIndexNameAndTypeAtRuntime() {

		// given
		Map<String, Object> person1 = new HashMap<>();
		person1.put("userId", "1");
		person1.put("email", "smhdiu@gmail.com");
		person1.put("title", "Mr");
		person1.put("firstName", "Mohsin");
		person1.put("lastName", "Husen");

		Map<String, Object> person2 = new HashMap<>();
		person2.put("userId", "2");
		person2.put("email", "akonczak@gmail.com");
		person2.put("title", "Mr");
		person2.put("firstName", "Artur");
		person2.put("lastName", "Konczak");

		IndexQuery indexQuery1 = new IndexQuery();
		indexQuery1.setId("1");
		indexQuery1.setObject(person1);

		IndexQuery indexQuery2 = new IndexQuery();
		indexQuery2.setId("2");
		indexQuery2.setObject(person2);

		List<IndexQuery> indexQueries = new ArrayList<>();
		indexQueries.add(indexQuery1);
		indexQueries.add(indexQuery2);

		// when
		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		SearchHits<Map> searchHits = operations.search(searchQuery, Map.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(searchHits.getTotalHits()).isEqualTo(2);
		assertThat(searchHits.getSearchHit(0).getContent().get("userId")).isEqualTo(person1.get("userId"));
		assertThat(searchHits.getSearchHit(1).getContent().get("userId")).isEqualTo(person2.get("userId"));
	}

	@Test // DATAES-523
	public void shouldIndexGteEntityWithVersionType() {

		// given
		String documentId = nextIdAsString();

		GTEVersionEntity entity = new GTEVersionEntity();
		entity.setId(documentId);
		entity.setName("FooBar");
		entity.setVersion(System.currentTimeMillis());

		IndexQueryBuilder indexQueryBuilder = new IndexQueryBuilder().withId(documentId).withVersion(entity.getVersion())
				.withObject(entity);

		IndexCoordinates index = IndexCoordinates.of(indexNameProvider.indexName());
		operations.index(indexQueryBuilder.build(), index);

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		// when
		SearchHits<GTEVersionEntity> entities = operations.search(searchQuery, GTEVersionEntity.class, index);
		// then
		assertThat(entities).isNotNull();
		assertThat(entities.getTotalHits()).isGreaterThanOrEqualTo(1);

		// reindex with same version
		operations.index(indexQueryBuilder.build(), index);

		// reindex with version one below
		assertThatThrownBy(() -> operations.index(indexQueryBuilder.withVersion(entity.getVersion() - 1).build(), index))
				.hasMessageContaining("version").hasMessageContaining("conflict");
	}

	@Test
	public void shouldIndexSampleEntityWithIndexAtRuntime() {

		String indexName = "custom-" + indexNameProvider.indexName();

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = new IndexQueryBuilder().withId(documentId).withObject(sampleEntity).build();

		operations.index(indexQuery, IndexCoordinates.of(indexName));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexName));

		// then
		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getTotalHits()).isGreaterThanOrEqualTo(1);
	}

	@Test // DATAES-106
	public void shouldReturnCountForGivenCriteriaQueryWithGivenIndexUsingCriteriaQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());

		// when
		long count = operations.count(criteriaQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(count).isEqualTo(1);
	}

	@Test // DATAES-67
	public void shouldReturnCountForGivenSearchQueryWithGivenIndexUsingSearchQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when
		long count = operations.count(searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(count).isEqualTo(1);
	}

	@Test // DATAES-106
	public void shouldReturnCountForGivenCriteriaQueryWithGivenIndexAndTypeUsingCriteriaQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());

		// when
		long count = operations.count(criteriaQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(count).isEqualTo(1);
	}

	@Test // DATAES-67
	public void shouldReturnCountForGivenSearchQueryWithGivenIndexAndTypeUsingSearchQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when
		long count = operations.count(searchQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(count).isEqualTo(1);
	}

	@Test // DATAES-106
	public void shouldReturnCountForGivenCriteriaQueryWithGivenMultiIndices() {

		// given
		cleanUpIndices();
		String documentId1 = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId1).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery1 = new IndexQueryBuilder().withId(sampleEntity1.getId()).withObject(sampleEntity1).build();

		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("some test message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery2 = new IndexQueryBuilder().withId(sampleEntity2.getId()).withObject(sampleEntity2).build();

		operations.index(indexQuery1, IndexCoordinates.of(INDEX_1_NAME));
		operations.index(indexQuery2, IndexCoordinates.of(INDEX_2_NAME));
		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME)).refresh();
		operations.indexOps(IndexCoordinates.of(INDEX_2_NAME)).refresh();

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());

		// when
		long count = operations.count(criteriaQuery, IndexCoordinates.of(INDEX_1_NAME, INDEX_2_NAME));

		// then
		assertThat(count).isEqualTo(2);
	}

	@Test // DATAES-67
	public void shouldReturnCountForGivenSearchQueryWithGivenMultiIndices() {

		// given
		cleanUpIndices();
		String documentId1 = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId1).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery1 = new IndexQueryBuilder().withId(sampleEntity1.getId()).withObject(sampleEntity1).build();

		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("some test message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery2 = new IndexQueryBuilder().withId(sampleEntity2.getId()).withObject(sampleEntity2).build();

		operations.index(indexQuery1, IndexCoordinates.of(INDEX_1_NAME));
		operations.index(indexQuery2, IndexCoordinates.of(INDEX_2_NAME));
		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME)).refresh();
		operations.indexOps(IndexCoordinates.of(INDEX_2_NAME)).refresh();

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when
		long count = operations.count(searchQuery, IndexCoordinates.of(INDEX_1_NAME, INDEX_2_NAME));

		// then
		assertThat(count).isEqualTo(2);
	}

	private void cleanUpIndices() {
		IndexOperations indexOperations1 = operations.indexOps(IndexCoordinates.of(INDEX_1_NAME));
		indexOperations1.delete();
		IndexOperations indexOperations2 = operations.indexOps(IndexCoordinates.of(INDEX_2_NAME));
		indexOperations2.delete();
		indexOperations1.create();
		indexOperations2.create();
		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME, INDEX_2_NAME)).refresh();
	}

	@Test // DATAES-71
	public void shouldCreatedIndexWithSpecifiedIndexName() {

		// given
		operations.indexOps(IndexCoordinates.of(INDEX_3_NAME)).delete();

		// when
		operations.indexOps(IndexCoordinates.of(INDEX_3_NAME)).create();

		// then
		assertThat(operations.indexOps(IndexCoordinates.of(INDEX_3_NAME)).exists()).isTrue();
	}

	@Test // DATAES-72
	public void shouldDeleteIndexForSpecifiedIndexName() {

		// given
		String indexName = "some-random-index";
		IndexCoordinates index = IndexCoordinates.of(indexName);
		operations.indexOps(index).create();
		operations.indexOps(index).refresh();

		// when
		operations.indexOps(index).delete();

		// then
		assertThat(operations.indexOps(index).exists()).isFalse();
	}

	@Test // DATAES-106
	public void shouldReturnCountForGivenCriteriaQueryWithGivenIndexNameForSpecificIndex() {

		// given
		cleanUpIndices();
		String documentId1 = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId1).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery1 = new IndexQueryBuilder().withId(sampleEntity1.getId()).withObject(sampleEntity1).build();

		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("some test message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery2 = new IndexQueryBuilder().withId(sampleEntity2.getId()).withObject(sampleEntity2).build();

		operations.index(indexQuery1, IndexCoordinates.of(INDEX_1_NAME));
		operations.index(indexQuery2, IndexCoordinates.of(INDEX_2_NAME));
		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME)).refresh();
		operations.indexOps(IndexCoordinates.of(INDEX_2_NAME)).refresh();

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());

		// when
		long count = operations.count(criteriaQuery, IndexCoordinates.of(INDEX_1_NAME));

		// then
		assertThat(count).isEqualTo(1);
	}

	@Test // DATAES-67
	public void shouldReturnCountForGivenSearchQueryWithGivenIndexNameForSpecificIndex() {

		// given
		cleanUpIndices();
		String documentId1 = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId1).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery1 = new IndexQueryBuilder().withId(sampleEntity1.getId()).withObject(sampleEntity1).build();

		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("some test message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery2 = new IndexQueryBuilder().withId(sampleEntity2.getId()).withObject(sampleEntity2).build();

		operations.index(indexQuery1, IndexCoordinates.of(INDEX_1_NAME));
		operations.index(indexQuery2, IndexCoordinates.of(INDEX_2_NAME));
		operations.indexOps(IndexCoordinates.of(INDEX_1_NAME)).refresh();
		operations.indexOps(IndexCoordinates.of(INDEX_2_NAME)).refresh();

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when
		long count = operations.count(searchQuery, IndexCoordinates.of(INDEX_1_NAME));

		// then
		assertThat(count).isEqualTo(1);
	}

	@Test
	public void shouldThrowAnExceptionForGivenCriteriaQueryWhenNoIndexSpecifiedForCountQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria());

		// when
		assertThatThrownBy(() -> operations.count(criteriaQuery, (IndexCoordinates) null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test // DATAES-67
	public void shouldThrowAnExceptionForGivenSearchQueryWhenNoIndexSpecifiedForCountQuery() {

		// given
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery = getIndexQuery(sampleEntity);
		operations.index(indexQuery, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when
		assertThatThrownBy(() -> operations.count(searchQuery, (IndexCoordinates) null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test // DATAES-71
	public void shouldCreateIndexWithGivenSettings() {

		// given
		String settings = "{\n" + "        \"index\": {\n" + "            \"number_of_shards\": \"1\",\n"
				+ "            \"number_of_replicas\": \"0\",\n" + "            \"analysis\": {\n"
				+ "                \"analyzer\": {\n" + "                    \"emailAnalyzer\": {\n"
				+ "                        \"type\": \"custom\",\n"
				+ "                        \"tokenizer\": \"uax_url_email\"\n" + "                    }\n"
				+ "                }\n" + "            }\n" + "        }\n" + '}';

		IndexOperations indexOperations3 = operations.indexOps(IndexCoordinates.of(INDEX_3_NAME));
		indexOperations3.delete();

		// when
		operations.indexOps(IndexCoordinates.of(INDEX_3_NAME)).create(parse(settings));

		// then
		Map map = indexOperations3.getSettings();
		assertThat(indexOperations3.exists()).isTrue();
		assertThat(map.containsKey("index.analysis.analyzer.emailAnalyzer.tokenizer")).isTrue();
		assertThat(map.get("index.analysis.analyzer.emailAnalyzer.tokenizer")).isEqualTo("uax_url_email");
	}

	@Test // DATAES-71
	public void shouldCreateGivenSettingsForGivenIndex() {

		// given
		// delete , create and apply mapping in before method

		// then
		Map map = indexOperations.getSettings();
		assertThat(indexOperations.exists()).isTrue();
		assertThat(map.containsKey("index.refresh_interval")).isTrue();
		assertThat(map.containsKey("index.number_of_replicas")).isTrue();
		assertThat(map.containsKey("index.number_of_shards")).isTrue();
		assertThat(map.containsKey("index.store.type")).isTrue();
		assertThat(map.get("index.refresh_interval")).isEqualTo("-1");
		assertThat(map.get("index.number_of_replicas")).isEqualTo("0");
		assertThat(map.get("index.number_of_shards")).isEqualTo("1");
		assertThat(map.get("index.store.type")).isEqualTo("fs");
	}

	@Test // DATAES-88
	public void shouldCreateIndexWithGivenClassAndSettings() {

		// given
		String settings = "{\n" + "        \"index\": {\n" + "            \"number_of_shards\": \"1\",\n"
				+ "            \"number_of_replicas\": \"0\",\n" + "            \"analysis\": {\n"
				+ "                \"analyzer\": {\n" + "                    \"emailAnalyzer\": {\n"
				+ "                        \"type\": \"custom\",\n"
				+ "                        \"tokenizer\": \"uax_url_email\"\n" + "                    }\n"
				+ "                }\n" + "            }\n" + "        }\n" + '}';

		// when
		indexOperations.delete();
		indexOperations.create(parse(settings));
		indexOperations.putMapping(SampleEntity.class);

		// then
		Map map = indexOperations.getSettings();
		assertThat(operations.indexOps(IndexCoordinates.of(indexNameProvider.indexName())).exists()).isTrue();
		assertThat(map.containsKey("index.number_of_replicas")).isTrue();
		assertThat(map.containsKey("index.number_of_shards")).isTrue();
		assertThat((String) map.get("index.number_of_replicas")).isEqualTo("0");
		assertThat((String) map.get("index.number_of_shards")).isEqualTo("1");
	}

	@Test
	public void shouldTestResultsAcrossMultipleIndices() {
		IndexCoordinates index1 = IndexCoordinates.of(INDEX_1_NAME);
		IndexCoordinates index2 = IndexCoordinates.of(INDEX_2_NAME);
		operations.indexOps(index1).delete();
		operations.indexOps(index2).delete();

		String documentId1 = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId1).message("some message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery1 = new IndexQueryBuilder().withId(sampleEntity1.getId()).withObject(sampleEntity1).build();

		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("some test message")
				.version(System.currentTimeMillis()).build();

		IndexQuery indexQuery2 = new IndexQueryBuilder().withId(sampleEntity2.getId()).withObject(sampleEntity2).build();

		operations.index(indexQuery1, index1);
		operations.index(indexQuery2, index2);

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();

		// when
		SearchHits<SampleEntity> sampleEntities = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(INDEX_1_NAME, INDEX_2_NAME));

		// then
		assertThat(sampleEntities).hasSize(2);
	}

	@Test
	/*
	 * This is basically a demonstration to show composing entities out of heterogeneous indexes.
	 */
	public void shouldComposeObjectsReturnedFromHeterogeneousIndexes() {

		IndexCoordinates index1 = IndexCoordinates.of(INDEX_1_NAME);
		IndexCoordinates index2 = IndexCoordinates.of(INDEX_2_NAME);
		operations.indexOps(index1).delete();
		operations.indexOps(index2).delete();

		HetroEntity1 entity1 = new HetroEntity1(nextIdAsString(), "aFirstName");
		HetroEntity2 entity2 = new HetroEntity2(nextIdAsString(), "aLastName");

		IndexQuery indexQuery1 = new IndexQueryBuilder().withId(entity1.getId()).withObject(entity1).build();
		IndexQuery indexQuery2 = new IndexQueryBuilder().withId(entity2.getId()).withObject(entity2).build();

		operations.index(indexQuery1, index1);
		operations.index(indexQuery2, index2);

		// when
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		SearchHits<ResultAggregator> page = operations.search(searchQuery, ResultAggregator.class,
				IndexCoordinates.of(INDEX_1_NAME, INDEX_2_NAME));

		assertThat(page.getTotalHits()).isEqualTo(2);
	}

	@Test
	public void shouldCreateIndexUsingServerDefaultConfiguration() {
		// given
		IndexOperations indexOps = operations.indexOps(UseServerConfigurationEntity.class);

		// when
		boolean created = indexOps.create();

		// then
		assertThat(created).isTrue();
		Map setting = indexOps.getSettings();
		assertThat(setting.get("index.number_of_shards")).isEqualTo("1");
		assertThat(setting.get("index.number_of_replicas")).isEqualTo("1");
	}

	@Test // DATAES-531
	public void shouldReturnMappingForGivenEntityClass() {

		// given

		// when
		Map<String, Object> mapping = indexOperations.getMapping();

		// then
		assertThat(mapping).isNotNull();
		assertThat(((Map<String, Object>) ((Map<String, Object>) mapping.get("properties")).get("message")).get("type"))
				.isEqualTo("text");
	}

	@Test // DATAES-525
	public void shouldDeleteOnlyDocumentsMatchedByDeleteQuery() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();
		// document to be deleted
		String documentIdToDelete = UUID.randomUUID().toString();
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(documentIdToDelete).message("some message")
				.version(System.currentTimeMillis()).build()));

		// remaining document
		String remainingDocumentId = UUID.randomUUID().toString();
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(remainingDocumentId).message("some other message")
				.version(System.currentTimeMillis()).build()));
		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		Query query = operations.idsQuery(Arrays.asList(documentIdToDelete));
		operations.delete(query, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		// document with id "remainingDocumentId" should still be indexed
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		assertThat(searchHits.getSearchHit(0).getContent().getId()).isEqualTo(remainingDocumentId);
	}

	@Test // DATAES-525
	public void shouldDeleteOnlyDocumentsMatchedByCriteriaQuery() {

		List<IndexQuery> indexQueries = new ArrayList<>();

		// given
		// document to be deleted
		String documentIdToDelete = UUID.randomUUID().toString();
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(documentIdToDelete).message("some message")
				.version(System.currentTimeMillis()).build()));

		// remaining document
		String remainingDocumentId = UUID.randomUUID().toString();
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(remainingDocumentId).message("some other message")
				.version(System.currentTimeMillis()).build()));
		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria("id").is(documentIdToDelete));
		operations.delete(criteriaQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		// document with id "remainingDocumentId" should still be indexed
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		assertThat(searchHits.getSearchHit(0).getContent().getId()).isEqualTo(remainingDocumentId);
	}

	@Test // DATAES-525
	public void shouldDeleteDocumentForGivenIdOnly() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();
		// document to be deleted
		String documentIdToDelete = UUID.randomUUID().toString();
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(documentIdToDelete).message("some message")
				.version(System.currentTimeMillis()).build()));

		// remaining document
		String remainingDocumentId = UUID.randomUUID().toString();
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(remainingDocumentId).message("some other message")
				.version(System.currentTimeMillis()).build()));
		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		operations.delete(documentIdToDelete, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		// document with id "remainingDocumentId" should still be indexed
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));
		assertThat(searchHits.getTotalHits()).isEqualTo(1L);
		assertThat(searchHits.getSearchHit(0).getContent().getId()).isEqualTo(remainingDocumentId);
	}

	@Test // DATAES-525
	public void shouldApplyCriteriaQueryToScanAndScrollForGivenCriteriaQuery() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(UUID.randomUUID().toString())
				.message("some message that should be found by the scroll query").version(System.currentTimeMillis()).build()));
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(UUID.randomUUID().toString())
				.message("some other message that should be found by the scroll query").version(System.currentTimeMillis())
				.build()));
		String notFindableMessage = "this entity must not be found by the scroll query";
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(UUID.randomUUID().toString()).message(notFindableMessage)
				.version(System.currentTimeMillis()).build()));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria("message").contains("message"));
		criteriaQuery.setPageable(PageRequest.of(0, 10));

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				criteriaQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scroll.getScrollId(), 1000,
					SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scroll.getScrollId());

		// then
		assertThat(sampleEntities).hasSize(2);
		assertThat(
				sampleEntities.stream().map(SearchHit::getContent).map(SampleEntity::getMessage).collect(Collectors.toList()))
						.doesNotContain(notFindableMessage);
	}

	@Test // DATAES-525
	public void shouldApplySearchQueryToScanAndScrollForGivenSearchQuery() {

		// given
		List<IndexQuery> indexQueries = new ArrayList<>();
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(UUID.randomUUID().toString())
				.message("some message that should be found by the scroll query").version(System.currentTimeMillis()).build()));
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(UUID.randomUUID().toString())
				.message("some other message that should be found by the scroll query").version(System.currentTimeMillis())
				.build()));
		String notFindableMessage = "this entity must not be found by the scroll query";
		indexQueries.add(getIndexQuery(SampleEntity.builder().id(UUID.randomUUID().toString()).message(notFindableMessage)
				.version(System.currentTimeMillis()).build()));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		// when
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchQuery("message", "message"))
				.withPageable(PageRequest.of(0, 10)).build();

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scroll.getScrollId(), 1000,
					SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scroll.getScrollId());

		// then
		assertThat(sampleEntities).hasSize(2);
		assertThat(
				sampleEntities.stream().map(SearchHit::getContent).map(SampleEntity::getMessage).collect(Collectors.toList()))
						.doesNotContain(notFindableMessage);
	}

	@Test // DATAES-565
	public void shouldRespectSourceFilterWithScanAndScrollForGivenSearchQuery() {

		// given
		List<IndexQuery> entities = createSampleEntitiesWithMessage("Test message", 3);

		// when
		operations.bulkIndex(entities, IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		SourceFilter sourceFilter = new FetchSourceFilterBuilder().withIncludes("id").build();

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withPageable(PageRequest.of(0, 10)).withSourceFilter(sourceFilter).build();

		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scroll.getScrollId(), 1000,
					SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		}
		((AbstractElasticsearchTemplate) operations).searchScrollClear(scroll.getScrollId());
		assertThat(sampleEntities).hasSize(3);
		assertThat(sampleEntities.stream().map(SearchHit::getContent).map(SampleEntity::getId).collect(Collectors.toList()))
				.doesNotContain((String) null);
		assertThat(
				sampleEntities.stream().map(SearchHit::getContent).map(SampleEntity::getMessage).collect(Collectors.toList()))
						.containsOnly((String) null);
	}

	@Test // DATAES-457
	public void shouldSortResultsGivenSortCriteriaWithScanAndScroll() {

		// given
		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("abc").rate(10)
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("xyz").rate(5)
				.version(System.currentTimeMillis()).build();

		// third document
		String documentId3 = nextIdAsString();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(documentId3).message("xyz").rate(10)
				.version(System.currentTimeMillis()).build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2, sampleEntity3));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withSort(new FieldSortBuilder("rate").order(SortOrder.ASC))
				.withSort(new FieldSortBuilder("message").order(SortOrder.DESC)).withPageable(PageRequest.of(0, 10)).build();

		// when
		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scroll.getScrollId(), 1000,
					SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		}

		// then
		assertThat(sampleEntities).hasSize(3);
		assertThat(sampleEntities.get(0).getContent().getRate()).isEqualTo(sampleEntity2.getRate());
		assertThat(sampleEntities.get(1).getContent().getRate()).isEqualTo(sampleEntity3.getRate());
		assertThat(sampleEntities.get(1).getContent().getMessage()).isEqualTo(sampleEntity3.getMessage());
		assertThat(sampleEntities.get(2).getContent().getRate()).isEqualTo(sampleEntity1.getRate());
		assertThat(sampleEntities.get(2).getContent().getMessage()).isEqualTo(sampleEntity1.getMessage());
	}

	@Test // DATAES-457
	public void shouldSortResultsGivenSortCriteriaFromPageableWithScanAndScroll() {

		// given
		// first document
		String documentId = nextIdAsString();
		SampleEntity sampleEntity1 = SampleEntity.builder().id(documentId).message("abc").rate(10)
				.version(System.currentTimeMillis()).build();

		// second document
		String documentId2 = nextIdAsString();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(documentId2).message("xyz").rate(5)
				.version(System.currentTimeMillis()).build();

		// third document
		String documentId3 = nextIdAsString();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(documentId3).message("xyz").rate(10)
				.version(System.currentTimeMillis()).build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(sampleEntity1, sampleEntity2, sampleEntity3));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withPageable(
						PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "rate").and(Sort.by(Sort.Direction.DESC, "message"))))
				.build();

		// when
		SearchScrollHits<SampleEntity> scroll = ((AbstractElasticsearchTemplate) operations).searchScrollStart(1000,
				searchQuery, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		List<SearchHit<SampleEntity>> sampleEntities = new ArrayList<>();
		while (scroll.hasSearchHits()) {
			sampleEntities.addAll(scroll.getSearchHits());
			scroll = ((AbstractElasticsearchTemplate) operations).searchScrollContinue(scroll.getScrollId(), 1000,
					SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		}

		// then
		assertThat(sampleEntities).hasSize(3);
		assertThat(sampleEntities.get(0).getContent().getRate()).isEqualTo(sampleEntity2.getRate());
		assertThat(sampleEntities.get(1).getContent().getRate()).isEqualTo(sampleEntity3.getRate());
		assertThat(sampleEntities.get(1).getContent().getMessage()).isEqualTo(sampleEntity3.getMessage());
		assertThat(sampleEntities.get(2).getContent().getRate()).isEqualTo(sampleEntity1.getRate());
		assertThat(sampleEntities.get(2).getContent().getMessage()).isEqualTo(sampleEntity1.getMessage());
	}

	@Test // DATAES-593
	public void shouldReturnDocumentWithCollapsedField() {

		// given
		SampleEntity sampleEntity = SampleEntity.builder().id(nextIdAsString()).message("message 1").rate(1)
				.version(System.currentTimeMillis()).build();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(nextIdAsString()).message("message 2").rate(2)
				.version(System.currentTimeMillis()).build();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(nextIdAsString()).message("message 1").rate(1)
				.version(System.currentTimeMillis()).build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(sampleEntity, sampleEntity2, sampleEntity3));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).withCollapseField("rate")
				.build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getTotalHits()).isEqualTo(3);
		assertThat(searchHits.getSearchHits()).hasSize(2);
		assertThat(searchHits.getSearchHit(0).getContent().getMessage()).isEqualTo("message 1");
		assertThat(searchHits.getSearchHit(1).getContent().getMessage()).isEqualTo("message 2");
	}

	@Test // #1493
	@DisplayName("should return document with collapse field and inner hits")
	public void shouldReturnDocumentWithCollapsedFieldAndInnerHits() {

		// given
		SampleEntity sampleEntity = SampleEntity.builder().id(nextIdAsString()).message("message 1").rate(1)
				.version(System.currentTimeMillis()).build();
		SampleEntity sampleEntity2 = SampleEntity.builder().id(nextIdAsString()).message("message 2").rate(2)
				.version(System.currentTimeMillis()).build();
		SampleEntity sampleEntity3 = SampleEntity.builder().id(nextIdAsString()).message("message 1").rate(1)
				.version(System.currentTimeMillis()).build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(sampleEntity, sampleEntity2, sampleEntity3));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withCollapseBuilder(new CollapseBuilder("rate").setInnerHits(new InnerHitBuilder("innerHits"))).build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getTotalHits()).isEqualTo(3);
		assertThat(searchHits.getSearchHits()).hasSize(2);
		assertThat(searchHits.getSearchHit(0).getContent().getMessage()).isEqualTo("message 1");
		assertThat(searchHits.getSearchHit(0).getInnerHits("innerHits").getTotalHits()).isEqualTo(2);
		assertThat(searchHits.getSearchHit(1).getContent().getMessage()).isEqualTo("message 2");
		assertThat(searchHits.getSearchHit(1).getInnerHits("innerHits").getTotalHits()).isEqualTo(1);
	}

	@Test // #1997
	@DisplayName("should return document with inner hits size zero")
	void shouldReturnDocumentWithInnerHitsSizeZero() {

		// given
		SampleEntity sampleEntity = SampleEntity.builder().id(nextIdAsString()).message("message 1").rate(1)
				.version(System.currentTimeMillis()).build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(sampleEntity));

		operations.bulkIndex(indexQueries, IndexCoordinates.of(indexNameProvider.indexName()));

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withCollapseBuilder(new CollapseBuilder("rate").setInnerHits(new InnerHitBuilder("innerHits").setSize(0)))
				.build();

		// when
		SearchHits<SampleEntity> searchHits = operations.search(searchQuery, SampleEntity.class,
				IndexCoordinates.of(indexNameProvider.indexName()));

		// then
		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		assertThat(searchHits.getSearchHits()).hasSize(1);
		assertThat(searchHits.getSearchHit(0).getContent().getMessage()).isEqualTo("message 1");
	}

	private IndexQuery getIndexQuery(SampleEntity sampleEntity) {
		return new IndexQueryBuilder().withId(sampleEntity.getId()).withObject(sampleEntity)
				.withVersion(sampleEntity.getVersion()).build();
	}

	private List<IndexQuery> getIndexQueries(List<SampleEntity> sampleEntities) {
		List<IndexQuery> indexQueries = new ArrayList<>();
		for (SampleEntity sampleEntity : sampleEntities) {
			indexQueries.add(getIndexQuery(sampleEntity));
		}
		return indexQueries;
	}

	@Document(indexName = INDEX_2_NAME)
	class ResultAggregator {

		private String id;
		private String firstName;
		private String lastName;

		ResultAggregator(String id, String firstName, String lastName) {
			this.id = id;
			this.firstName = firstName;
			this.lastName = lastName;
		}
	}

	@Test // DATAES-187
	public void shouldUsePageableOffsetToSetFromInSearchRequest() {

		// given
		Pageable pageable = new PageRequest(1, 10, Sort.unsorted()) {
			@Override
			public long getOffset() {
				return 30;
			}
		};

		NativeSearchQuery query = new NativeSearchQueryBuilder() //
				.withPageable(pageable) //
				.build();

		// when
		SearchRequest searchRequest = getRequestFactory().searchRequest(query, null, IndexCoordinates.of("test"));

		// then
		assertThat(searchRequest.source().from()).isEqualTo(30);
	}

	@Test // DATAES-709
	public void shouldNotIncludeDefaultsGetIndexSettings() {

		// given
		// when
		Map<String, Object> map = indexOperations.getSettings();

		// then
		assertThat(map).doesNotContainKey("index.max_result_window");
	}

	@Test // DATAES-709
	public void shouldIncludeDefaultsOnGetIndexSettings() {

		// given
		// when
		Map<String, Object> map = indexOperations.getSettings(true);

		// then
		assertThat(map).containsKey("index.max_result_window");
	}

	@Test // DATAES-714
	void shouldReturnSortFieldsInSearchHits() {
		IndexCoordinates index = IndexCoordinates.of(indexNameProvider.indexName());
		IndexOperations indexOperations = operations.indexOps(SearchHitsEntity.class);
		indexOperations.delete();
		indexOperations.createWithMapping();

		SearchHitsEntity entity = new SearchHitsEntity();
		entity.setId("1");
		entity.setNumber(1000L);
		entity.setKeyword("thousands");
		IndexQuery indexQuery = new IndexQueryBuilder().withId(entity.getId()).withObject(entity).build();
		operations.index(indexQuery, index);

		NativeSearchQuery query = new NativeSearchQueryBuilder() //
				.withQuery(matchAllQuery()) //
				.withSort(new FieldSortBuilder("keyword").order(SortOrder.ASC))
				.withSort(new FieldSortBuilder("number").order(SortOrder.DESC)).build();

		SearchHits<SearchHitsEntity> searchHits = operations.search(query, SearchHitsEntity.class);

		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getSearchHits()).hasSize(1);

		SearchHit<SearchHitsEntity> searchHit = searchHits.getSearchHit(0);
		List<Object> sortValues = searchHit.getSortValues();
		assertThat(sortValues).hasSize(2);
		assertThat(sortValues.get(0)).isInstanceOf(String.class).isEqualTo("thousands");
		// transport client returns Long, rest client Integer
		java.lang.Object o = sortValues.get(1);
		if (o instanceof Integer) {
			Integer i = (Integer) o;
			assertThat(o).isInstanceOf(Integer.class).isEqualTo(1000);
		} else if (o instanceof Long) {
			Long l = (Long) o;
			assertThat(o).isInstanceOf(Long.class).isEqualTo(1000L);
		} else {
			fail("unexpected object type " + o);
		}
	}

	@Test // DATAES-715
	void shouldReturnHighlightFieldsInSearchHit() {
		IndexCoordinates index = IndexCoordinates.of("test-index-highlight-entity-template");
		HighlightEntity entity = new HighlightEntity("1",
				"This message is a long text which contains the word to search for "
						+ "in two places, the first being near the beginning and the second near the end of the message");
		IndexQuery indexQuery = new IndexQueryBuilder().withId(entity.getId()).withObject(entity).build();
		operations.index(indexQuery, index);
		operations.indexOps(index).refresh();

		NativeSearchQuery query = new NativeSearchQueryBuilder() //
				.withQuery(termQuery("message", "message")) //
				.withHighlightFields(new HighlightBuilder.Field("message")) //
				.build();

		SearchHits<HighlightEntity> searchHits = operations.search(query, HighlightEntity.class, index);

		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getSearchHits()).hasSize(1);

		SearchHit<HighlightEntity> searchHit = searchHits.getSearchHit(0);
		List<String> highlightField = searchHit.getHighlightField("message");
		assertThat(highlightField).hasSize(2);
		assertThat(highlightField.get(0)).contains("<em>message</em>");
		assertThat(highlightField.get(1)).contains("<em>message</em>");
	}

	@Test // #1686
	void shouldRunRescoreQueryInSearchQuery() {
		IndexCoordinates index = IndexCoordinates.of("test-index-rescore-entity-template");

		// matches main query better
		SampleEntity entity = SampleEntity.builder() //
				.id("1") //
				.message("some message") //
				.rate(java.lang.Integer.MAX_VALUE).version(System.currentTimeMillis()) //
				.build();

		// high score from rescore query
		SampleEntity entity2 = SampleEntity.builder() //
				.id("2") //
				.message("nothing") //
				.rate(1).version(System.currentTimeMillis()) //
				.build();

		List<IndexQuery> indexQueries = getIndexQueries(Arrays.asList(entity, entity2));

		operations.bulkIndex(indexQueries, index);

		NativeSearchQuery query = new NativeSearchQueryBuilder() //
				.withQuery(boolQuery().filter(existsQuery("rate")).should(termQuery("message", "message"))) //
				.withRescorerQuery(
						new RescorerQuery(new NativeSearchQueryBuilder().withQuery(QueryBuilders
								.functionScoreQuery(new FunctionScoreQueryBuilder.FilterFunctionBuilder[] {
										new FilterFunctionBuilder(new GaussDecayFunctionBuilder("rate", 0, 10, null, 0.5).setWeight(1f)),
										new FilterFunctionBuilder(
												new GaussDecayFunctionBuilder("rate", 0, 10, null, 0.5).setWeight(100f)) })
								.scoreMode(FunctionScoreQuery.ScoreMode.SUM).maxBoost(80f).boostMode(CombineFunction.REPLACE)).build())
										.withScoreMode(ScoreMode.Max).withWindowSize(100))
				.build();

		SearchHits<SampleEntity> searchHits = operations.search(query, SampleEntity.class, index);

		assertThat(searchHits).isNotNull();
		assertThat(searchHits.getSearchHits()).hasSize(2);

		SearchHit<SampleEntity> searchHit = searchHits.getSearchHit(0);
		assertThat(searchHit.getContent().getMessage()).isEqualTo("nothing");
		// score capped to 80
		assertThat(searchHit.getScore()).isEqualTo(80f);
	}

	@Test
	// DATAES-738
	void shouldSaveEntityWithIndexCoordinates() {
		String id = "42";
		SampleEntity entity = new SampleEntity();
		entity.setId(id);
		entity.setVersion(42L);
		entity.setMessage("message");

		operations.save(entity, IndexCoordinates.of(indexNameProvider.indexName()));

		SampleEntity result = operations.get(id, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(result).isEqualTo(entity);
	}

	@Test // DATAES-738
	void shouldSaveEntityWithOutIndexCoordinates() {
		String id = "42";
		SampleEntity entity = new SampleEntity();
		entity.setId(id);
		entity.setVersion(42L);
		entity.setMessage("message");

		operations.save(entity);

		SampleEntity result = operations.get(id, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(result).isEqualTo(entity);
	}

	@Test // DATAES-738
	void shouldSaveEntityIterableWithIndexCoordinates() {
		String id1 = "42";
		SampleEntity entity1 = new SampleEntity();
		entity1.setId(id1);
		entity1.setVersion(42L);
		entity1.setMessage("message");
		String id2 = "43";
		SampleEntity entity2 = new SampleEntity();
		entity2.setId(id2);
		entity2.setVersion(43L);
		entity2.setMessage("message");

		operations.save(Arrays.asList(entity1, entity2), IndexCoordinates.of(indexNameProvider.indexName()));

		SampleEntity result1 = operations.get(id1, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		SampleEntity result2 = operations.get(id2, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(result1).isEqualTo(entity1);
		assertThat(result2).isEqualTo(entity2);
	}

	@Test // DATAES-738
	void shouldSaveEntityIterableWithoutIndexCoordinates() {
		String id1 = "42";
		SampleEntity entity1 = new SampleEntity();
		entity1.setId(id1);
		entity1.setVersion(42L);
		entity1.setMessage("message");
		String id2 = "43";
		SampleEntity entity2 = new SampleEntity();
		entity2.setId(id2);
		entity2.setVersion(43L);
		entity2.setMessage("message");

		operations.save(Arrays.asList(entity1, entity2));

		SampleEntity result1 = operations.get(id1, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));
		SampleEntity result2 = operations.get(id2, SampleEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		assertThat(result1).isEqualTo(entity1);
		assertThat(result2).isEqualTo(entity2);
	}

	@Test // DATAES-745
	void shouldDoExistsWithEntity() {
		String id = "42";
		SampleEntity entity = new SampleEntity();
		entity.setId(id);
		entity.setVersion(42L);
		entity.setMessage("message");

		operations.save(entity);

		assertThat(operations.exists("42", SampleEntity.class)).isTrue();
	}

	@Test // DATAES-745
	void shouldDoExistsWithIndexCoordinates() {
		String id = "42";
		SampleEntity entity = new SampleEntity();
		entity.setId(id);
		entity.setVersion(42L);
		entity.setMessage("message");

		operations.save(entity);

		assertThat(operations.exists("42", IndexCoordinates.of(indexNameProvider.indexName()))).isTrue();
	}

	@Test // DATAES-876
	void shouldReturnSeqNoPrimaryTermOnSave() {
		OptimisticEntity original = new OptimisticEntity();
		original.setMessage("It's fine");
		OptimisticEntity saved = operations.save(original);

		assertThatSeqNoPrimaryTermIsFilled(saved);
	}

	@Test // DATAES-876
	void shouldReturnSeqNoPrimaryTermOnBulkSave() {
		OptimisticEntity original1 = new OptimisticEntity();
		original1.setMessage("It's fine 1");
		OptimisticEntity original2 = new OptimisticEntity();
		original2.setMessage("It's fine 2");

		Iterable<OptimisticEntity> saved = operations.save(Arrays.asList(original1, original2));

		saved.forEach(optimisticEntity -> {
			assertThatSeqNoPrimaryTermIsFilled(optimisticEntity);
		});
	}

	@Test // DATAES-799
	void getShouldReturnSeqNoPrimaryTerm() {
		OptimisticEntity original = new OptimisticEntity();
		original.setMessage("It's fine");
		OptimisticEntity saved = operations.save(original);

		OptimisticEntity retrieved = operations.get(saved.getId(), OptimisticEntity.class);

		assertThatSeqNoPrimaryTermIsFilled(retrieved);
	}

	private void assertThatSeqNoPrimaryTermIsFilled(OptimisticEntity retrieved) {
		assertThat(retrieved.seqNoPrimaryTerm).isNotNull();
		assertThat(retrieved.seqNoPrimaryTerm.getSequenceNumber()).isNotNull();
		assertThat(retrieved.seqNoPrimaryTerm.getSequenceNumber()).isNotNegative();
		assertThat(retrieved.seqNoPrimaryTerm.getPrimaryTerm()).isNotNull();
		assertThat(retrieved.seqNoPrimaryTerm.getPrimaryTerm()).isPositive();
	}

	@Test // DATAES-799
	void multigetShouldReturnSeqNoPrimaryTerm() {
		OptimisticEntity original = new OptimisticEntity();
		original.setMessage("It's fine");
		OptimisticEntity saved = operations.save(original);
		operations.indexOps(OptimisticEntity.class).refresh();

		List<MultiGetItem<OptimisticEntity>> retrievedList = operations.multiGet(queryForOne(saved.getId()),
				OptimisticEntity.class, operations.getIndexCoordinatesFor(OptimisticEntity.class));
		OptimisticEntity retrieved = retrievedList.get(0).getItem();

		assertThatSeqNoPrimaryTermIsFilled(retrieved);
	}

	private Query queryForOne(String id) {
		return new NativeSearchQueryBuilder().withIds(singletonList(id)).build();
	}

	@Test // DATAES-799
	void searchShouldReturnSeqNoPrimaryTerm() {
		OptimisticEntity original = new OptimisticEntity();
		original.setMessage("It's fine");
		OptimisticEntity saved = operations.save(original);
		operations.indexOps(OptimisticEntity.class).refresh();

		SearchHits<OptimisticEntity> retrievedHits = operations.search(queryForOne(saved.getId()), OptimisticEntity.class);
		OptimisticEntity retrieved = retrievedHits.getSearchHit(0).getContent();

		assertThatSeqNoPrimaryTermIsFilled(retrieved);
	}

	@Test // DATAES-799
	void multiSearchShouldReturnSeqNoPrimaryTerm() {
		OptimisticEntity original = new OptimisticEntity();
		original.setMessage("It's fine");
		OptimisticEntity saved = operations.save(original);
		operations.indexOps(OptimisticEntity.class).refresh();

		List<Query> queries = singletonList(queryForOne(saved.getId()));
		List<SearchHits<OptimisticEntity>> retrievedHits = operations.multiSearch(queries, OptimisticEntity.class,
				operations.getIndexCoordinatesFor(OptimisticEntity.class));
		OptimisticEntity retrieved = retrievedHits.get(0).getSearchHit(0).getContent();

		assertThatSeqNoPrimaryTermIsFilled(retrieved);
	}

	@Test // DATAES-799
	void searchForStreamShouldReturnSeqNoPrimaryTerm() {
		OptimisticEntity original = new OptimisticEntity();
		original.setMessage("It's fine");
		OptimisticEntity saved = operations.save(original);
		operations.indexOps(OptimisticEntity.class).refresh();

		SearchHitsIterator<OptimisticEntity> retrievedHits = operations.searchForStream(queryForOne(saved.getId()),
				OptimisticEntity.class);
		OptimisticEntity retrieved = retrievedHits.next().getContent();

		assertThatSeqNoPrimaryTermIsFilled(retrieved);
	}

	@Test // DATAES-799
	void shouldThrowOptimisticLockingFailureExceptionWhenConcurrentUpdateOccursOnEntityWithSeqNoPrimaryTermProperty() {
		OptimisticEntity original = new OptimisticEntity();
		original.setMessage("It's fine");
		OptimisticEntity saved = operations.save(original);

		OptimisticEntity forEdit1 = operations.get(saved.getId(), OptimisticEntity.class);
		OptimisticEntity forEdit2 = operations.get(saved.getId(), OptimisticEntity.class);

		forEdit1.setMessage("It'll be ok");
		operations.save(forEdit1);

		forEdit2.setMessage("It'll be great");
		assertThatThrownBy(() -> operations.save(forEdit2)).isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test // DATAES-799
	void shouldThrowOptimisticLockingFailureExceptionWhenConcurrentUpdateOccursOnVersionedEntityWithSeqNoPrimaryTermProperty() {
		OptimisticAndVersionedEntity original = new OptimisticAndVersionedEntity();
		original.setMessage("It's fine");
		OptimisticAndVersionedEntity saved = operations.save(original);

		OptimisticAndVersionedEntity forEdit1 = operations.get(saved.getId(), OptimisticAndVersionedEntity.class);
		OptimisticAndVersionedEntity forEdit2 = operations.get(saved.getId(), OptimisticAndVersionedEntity.class);

		forEdit1.setMessage("It'll be ok");
		operations.save(forEdit1);

		forEdit2.setMessage("It'll be great");
		assertThatThrownBy(() -> operations.save(forEdit2)).isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test // DATAES-799
	void shouldAllowFullReplaceOfEntityWithBothSeqNoPrimaryTermAndVersion() {
		OptimisticAndVersionedEntity original = new OptimisticAndVersionedEntity();
		original.setMessage("It's fine");
		OptimisticAndVersionedEntity saved = operations.save(original);

		OptimisticAndVersionedEntity forEdit = operations.get(saved.getId(), OptimisticAndVersionedEntity.class);

		forEdit.setMessage("It'll be ok");
		operations.save(forEdit);
	}

	@Test
	void shouldSupportCRUDOpsForEntityWithJoinFields() throws Exception {
		String qId1 = java.util.UUID.randomUUID().toString();
		String qId2 = java.util.UUID.randomUUID().toString();
		String aId1 = java.util.UUID.randomUUID().toString();
		String aId2 = java.util.UUID.randomUUID().toString();

		// default maps SampleEntity, need a new mapping here
		IndexOperations indexOperations = operations.indexOps(SampleJoinEntity.class);
		indexOperations.delete();
		indexOperations.createWithMapping();

		shouldSaveEntityWithJoinFields(qId1, qId2, aId1, aId2);
		shouldUpdateEntityWithJoinFields(qId1, qId2, aId1, aId2);
		shouldDeleteEntityWithJoinFields(qId2, aId2);
	}

	// #1218
	private void shouldSaveEntityWithJoinFields(String qId1, String qId2, String aId1, String aId2) throws Exception {
		SampleJoinEntity sampleQuestionEntity1 = new SampleJoinEntity();
		sampleQuestionEntity1.setUuid(qId1);
		sampleQuestionEntity1.setText("This is a question");

		JoinField<String> myQJoinField1 = new JoinField<>("question");
		sampleQuestionEntity1.setMyJoinField(myQJoinField1);

		SampleJoinEntity sampleQuestionEntity2 = new SampleJoinEntity();
		sampleQuestionEntity2.setUuid(qId2);
		sampleQuestionEntity2.setText("This is another question");

		JoinField<String> myQJoinField2 = new JoinField<>("question");
		sampleQuestionEntity2.setMyJoinField(myQJoinField2);

		SampleJoinEntity sampleAnswerEntity1 = new SampleJoinEntity();
		sampleAnswerEntity1.setUuid(aId1);
		sampleAnswerEntity1.setText("This is an answer");

		JoinField<String> myAJoinField1 = new JoinField<>("answer");
		myAJoinField1.setParent(qId1);
		sampleAnswerEntity1.setMyJoinField(myAJoinField1);

		SampleJoinEntity sampleAnswerEntity2 = new SampleJoinEntity();
		sampleAnswerEntity2.setUuid(aId2);
		sampleAnswerEntity2.setText("This is another answer");

		JoinField<String> myAJoinField2 = new JoinField<>("answer");
		myAJoinField2.setParent(qId1);
		sampleAnswerEntity2.setMyJoinField(myAJoinField2);

		IndexCoordinates index = IndexCoordinates.of(indexNameProvider.indexName());
		operations.save(
				Arrays.asList(sampleQuestionEntity1, sampleQuestionEntity2, sampleAnswerEntity1, sampleAnswerEntity2), index);

		SearchHits<SampleJoinEntity> hits = operations.search(
				new NativeSearchQueryBuilder().withQuery(new ParentIdQueryBuilder("answer", qId1)).build(),
				SampleJoinEntity.class);

		List<String> hitIds = hits.getSearchHits().stream()
				.map(sampleJoinEntitySearchHit -> sampleJoinEntitySearchHit.getId()).collect(Collectors.toList());

		assertThat(hitIds.size()).isEqualTo(2);
		assertThat(hitIds.containsAll(Arrays.asList(aId1, aId2))).isTrue();

		hits.forEach(searchHit -> {
			assertThat(searchHit.getRouting()).isEqualTo(qId1);
		});
	}

	private void shouldUpdateEntityWithJoinFields(String qId1, String qId2, String aId1, String aId2) throws Exception {
		org.springframework.data.elasticsearch.core.document.Document document = org.springframework.data.elasticsearch.core.document.Document
				.create();
		document.put("myJoinField", toDocument(new JoinField<>("answer", qId2)));
		UpdateQuery updateQuery = UpdateQuery.builder(aId2) //
				.withDocument(document) //
				.withRouting(qId2).build();

		List<UpdateQuery> queries = new ArrayList<>();
		queries.add(updateQuery);

		// when
		operations.bulkUpdate(queries, IndexCoordinates.of(indexNameProvider.indexName()));

		SearchHits<SampleJoinEntity> updatedHits = operations.search(
				new NativeSearchQueryBuilder().withQuery(new ParentIdQueryBuilder("answer", qId2)).build(),
				SampleJoinEntity.class);

		List<String> hitIds = updatedHits.getSearchHits().stream().map(new Function<SearchHit<SampleJoinEntity>, String>() {
			@Override
			public String apply(SearchHit<SampleJoinEntity> sampleJoinEntitySearchHit) {
				return sampleJoinEntitySearchHit.getId();
			}
		}).collect(Collectors.toList());
		assertThat(hitIds.size()).isEqualTo(1);
		assertThat(hitIds.get(0)).isEqualTo(aId2);

		updatedHits = operations.search(
				new NativeSearchQueryBuilder().withQuery(new ParentIdQueryBuilder("answer", qId1)).build(),
				SampleJoinEntity.class);

		hitIds = updatedHits.getSearchHits().stream().map(new Function<SearchHit<SampleJoinEntity>, String>() {
			@Override
			public String apply(SearchHit<SampleJoinEntity> sampleJoinEntitySearchHit) {
				return sampleJoinEntitySearchHit.getId();
			}
		}).collect(Collectors.toList());
		assertThat(hitIds.size()).isEqualTo(1);
		assertThat(hitIds.get(0)).isEqualTo(aId1);
	}

	private void shouldDeleteEntityWithJoinFields(String qId2, String aId2) throws Exception {
		Query query = new NativeSearchQueryBuilder().withQuery(new ParentIdQueryBuilder("answer", qId2)).withRoute(qId2)
				.build();
		operations.delete(query, SampleJoinEntity.class, IndexCoordinates.of(indexNameProvider.indexName()));

		SearchHits<SampleJoinEntity> deletedHits = operations.search(
				new NativeSearchQueryBuilder().withQuery(new ParentIdQueryBuilder("answer", qId2)).build(),
				SampleJoinEntity.class);

		List<String> hitIds = deletedHits.getSearchHits().stream().map(new Function<SearchHit<SampleJoinEntity>, String>() {
			@Override
			public String apply(SearchHit<SampleJoinEntity> sampleJoinEntitySearchHit) {
				return sampleJoinEntitySearchHit.getId();
			}
		}).collect(Collectors.toList());
		assertThat(hitIds.size()).isEqualTo(0);
	}

	private org.springframework.data.elasticsearch.core.document.Document toDocument(JoinField joinField) {
		org.springframework.data.elasticsearch.core.document.Document document = create();
		document.put("name", joinField.getName());
		document.put("parent", joinField.getParent());
		return document;
	}

	protected RequestFactory getRequestFactory() {
		return ((AbstractElasticsearchTemplate) operations).getRequestFactory();
	}

	@Test // DATAES-908
	void shouldFillVersionOnSaveOne() {
		VersionedEntity saved = operations.save(new VersionedEntity());

		assertThat(saved.getVersion()).isNotNull();
	}

	@Test // DATAES-908
	void shouldFillVersionOnSaveIterable() {
		List<VersionedEntity> iterable = Arrays.asList(new VersionedEntity(), new VersionedEntity());
		Iterator<VersionedEntity> results = operations.save(iterable).iterator();
		VersionedEntity saved1 = results.next();
		VersionedEntity saved2 = results.next();

		assertThat(saved1.getVersion()).isNotNull();
		assertThat(saved2.getVersion()).isNotNull();
	}

	@Test // DATAES-908
	void shouldFillVersionOnSaveArray() {
		VersionedEntity[] array = { new VersionedEntity(), new VersionedEntity() };
		Iterator<VersionedEntity> results = operations.save(array).iterator();
		VersionedEntity saved1 = results.next();
		VersionedEntity saved2 = results.next();

		assertThat(saved1.getVersion()).isNotNull();
		assertThat(saved2.getVersion()).isNotNull();
	}

	@Test // DATAES-908
	void shouldFillVersionOnIndexOne() {
		VersionedEntity entity = new VersionedEntity();
		IndexQuery query = new IndexQueryBuilder().withObject(entity).build();
		operations.index(query, operations.getIndexCoordinatesFor(VersionedEntity.class));

		assertThat(entity.getVersion()).isNotNull();
	}

	@Test // DATAES-908
	void shouldFillVersionOnBulkIndex() {
		VersionedEntity entity1 = new VersionedEntity();
		VersionedEntity entity2 = new VersionedEntity();
		IndexQuery query1 = new IndexQueryBuilder().withObject(entity1).build();
		IndexQuery query2 = new IndexQueryBuilder().withObject(entity2).build();
		operations.bulkIndex(Arrays.asList(query1, query2), VersionedEntity.class);

		assertThat(entity1.getVersion()).isNotNull();
		assertThat(entity2.getVersion()).isNotNull();
	}

	@Test // DATAES-908
	void shouldFillSeqNoPrimaryKeyOnBulkIndex() {
		OptimisticEntity entity1 = new OptimisticEntity();
		OptimisticEntity entity2 = new OptimisticEntity();
		IndexQuery query1 = new IndexQueryBuilder().withObject(entity1).build();
		IndexQuery query2 = new IndexQueryBuilder().withObject(entity2).build();
		operations.bulkIndex(Arrays.asList(query1, query2), OptimisticEntity.class);

		assertThatSeqNoPrimaryTermIsFilled(entity1);
		assertThatSeqNoPrimaryTermIsFilled(entity2);
	}

	@Test // DATAES-907
	@DisplayName("should track_total_hits with default value")
	void shouldTrackTotalHitsWithDefaultValue() {

		NativeSearchQuery queryAll = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		operations.delete(queryAll, SampleEntity.class);

		List<SampleEntity> entities = IntStream.rangeClosed(1, 15_000)
				.mapToObj(i -> SampleEntity.builder().id("" + i).build()).collect(Collectors.toList());

		operations.save(entities);

		queryAll.setTrackTotalHits(null);
		SearchHits<SampleEntity> searchHits = operations.search(queryAll, SampleEntity.class);

		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(searchHits.getTotalHits()).isEqualTo((long) RequestFactory.INDEX_MAX_RESULT_WINDOW);
		softly.assertThat(searchHits.getTotalHitsRelation()).isEqualTo(TotalHitsRelation.GREATER_THAN_OR_EQUAL_TO);
		softly.assertAll();
	}

	@Test // DATAES-907
	@DisplayName("should track total hits")
	void shouldTrackTotalHits() {

		NativeSearchQuery queryAll = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		operations.delete(queryAll, SampleEntity.class);

		List<SampleEntity> entities = IntStream.rangeClosed(1, 15_000)
				.mapToObj(i -> SampleEntity.builder().id("" + i).build()).collect(Collectors.toList());

		operations.save(entities);

		queryAll.setTrackTotalHits(true);
		queryAll.setTrackTotalHitsUpTo(12_345);
		SearchHits<SampleEntity> searchHits = operations.search(queryAll, SampleEntity.class);

		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(searchHits.getTotalHits()).isEqualTo(15_000L);
		softly.assertThat(searchHits.getTotalHitsRelation()).isEqualTo(TotalHitsRelation.EQUAL_TO);
		softly.assertAll();
	}

	@Test // DATAES-907
	@DisplayName("should track total hits to specific value")
	void shouldTrackTotalHitsToSpecificValue() {

		NativeSearchQuery queryAll = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		operations.delete(queryAll, SampleEntity.class);

		List<SampleEntity> entities = IntStream.rangeClosed(1, 15_000)
				.mapToObj(i -> SampleEntity.builder().id("" + i).build()).collect(Collectors.toList());

		operations.save(entities);

		queryAll.setTrackTotalHits(null);
		queryAll.setTrackTotalHitsUpTo(12_345);
		SearchHits<SampleEntity> searchHits = operations.search(queryAll, SampleEntity.class);

		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(searchHits.getTotalHits()).isEqualTo(12_345L);
		softly.assertThat(searchHits.getTotalHitsRelation()).isEqualTo(TotalHitsRelation.GREATER_THAN_OR_EQUAL_TO);
		softly.assertAll();
	}

	@Test // DATAES-907
	@DisplayName("should track total hits is off")
	void shouldTrackTotalHitsIsOff() {

		NativeSearchQuery queryAll = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		operations.delete(queryAll, SampleEntity.class);

		List<SampleEntity> entities = IntStream.rangeClosed(1, 15_000)
				.mapToObj(i -> SampleEntity.builder().id("" + i).build()).collect(Collectors.toList());

		operations.save(entities);

		queryAll.setTrackTotalHits(false);
		queryAll.setTrackTotalHitsUpTo(12_345);
		SearchHits<SampleEntity> searchHits = operations.search(queryAll, SampleEntity.class);

		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(searchHits.getTotalHits()).isEqualTo(10_000L);
		softly.assertThat(searchHits.getTotalHitsRelation()).isEqualTo(TotalHitsRelation.OFF);
		softly.assertAll();
	}

	@Test // #725
	@DisplayName("should not return explanation when not requested")
	void shouldNotReturnExplanationWhenNotRequested() {

		SampleEntity entity = SampleEntity.builder().id("42").message("a message with text").build();
		operations.save(entity);
		Criteria criteria = new Criteria("message").contains("with");
		CriteriaQuery query = new CriteriaQuery(criteria);

		SearchHits<SampleEntity> searchHits = operations.search(query, SampleEntity.class);

		assertThat(searchHits.getTotalHits()).isEqualTo(1L);
		Explanation explanation = searchHits.getSearchHit(0).getExplanation();
		assertThat(explanation).isNull();
	}

	@Test // #725
	@DisplayName("should return explanation when requested")
	void shouldReturnExplanationWhenRequested() {

		SampleEntity entity = SampleEntity.builder().id("42").message("a message with text").build();
		operations.save(entity);
		Criteria criteria = new Criteria("message").contains("with");
		CriteriaQuery query = new CriteriaQuery(criteria);
		query.setExplain(true);

		SearchHits<SampleEntity> searchHits = operations.search(query, SampleEntity.class);

		assertThat(searchHits.getTotalHits()).isEqualTo(1L);
		Explanation explanation = searchHits.getSearchHit(0).getExplanation();
		assertThat(explanation).isNotNull();
	}

	@Test // #1800
	@DisplayName("should work with immutable classes")
	void shouldWorkWithImmutableClasses() {

		ImmutableEntity entity = new ImmutableEntity(null, "some text", null);

		ImmutableEntity saved = operations.save(entity);

		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isNotEmpty();
		SeqNoPrimaryTerm seqNoPrimaryTerm = saved.getSeqNoPrimaryTerm();
		assertThat(seqNoPrimaryTerm).isNotNull();

		ImmutableEntity retrieved = operations.get(saved.getId(), ImmutableEntity.class);

		assertThat(retrieved).isEqualTo(saved);
	}

	@Test // #1488
	@DisplayName("should set scripted fields on immutable objects")
	void shouldSetScriptedFieldsOnImmutableObjects() {

		ImmutableWithScriptedEntity entity = new ImmutableWithScriptedEntity("42", 42, null);
		operations.save(entity);

		Map<String, Object> params = new HashMap<>();
		params.put("factor", 2);
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery())
				.withSourceFilter(new FetchSourceFilterBuilder().withIncludes("*").build())
				.withScriptField(new ScriptField("scriptedRate",
						new Script(ScriptType.INLINE, "expression", "doc['rate'] * factor", params)))
				.build();

		SearchHits<ImmutableWithScriptedEntity> searchHits = operations.search(searchQuery,
				ImmutableWithScriptedEntity.class);

		assertThat(searchHits.getTotalHits()).isEqualTo(1);
		ImmutableWithScriptedEntity foundEntity = searchHits.getSearchHit(0).getContent();
		assertThat(foundEntity.getId()).isEqualTo("42");
		assertThat(foundEntity.getRate()).isEqualTo(42);
		assertThat(foundEntity.getScriptedRate()).isEqualTo(84.0);
	}

	@Test // #1893
	@DisplayName("should index document from source with version")
	void shouldIndexDocumentFromSourceWithVersion() {

		String source = "{\n" + //
				"  \"answer\": 42\n" + //
				"}";
		IndexQuery query = new IndexQueryBuilder() //
				.withId("42") //
				.withSource(source) //
				.withVersion(42L) //
				.build();

		operations.index(query, IndexCoordinates.of(indexNameProvider.indexName()));
	}

	@Test // #1945
	@DisplayName("should error on sort with unmapped field and default settings")
	void shouldErrorOnSortWithUnmappedFieldAndDefaultSettings() {

		Sort.Order order = new Sort.Order(Sort.Direction.ASC, "unmappedField");
		Query query = operations.matchAllQuery().addSort(Sort.by(order));

		assertThatThrownBy(() -> {
			operations.search(query, SampleEntity.class);
		});
	}

	@Test // #1945
	@DisplayName("should not error on sort with unmapped field and unmapped_type settings")
	void shouldNotErrorOnSortWithUnmappedFieldAndUnmappedTypeSettings() {

		org.springframework.data.elasticsearch.core.query.Order order = new org.springframework.data.elasticsearch.core.query.Order(
				Sort.Direction.ASC, "unmappedField").withUnmappedType("long");
		Query query = operations.matchAllQuery().addSort(Sort.by(order));

		operations.search(query, SampleEntity.class);
	}

	@Test // #1529
	void shouldWorkReindexForExistingIndex() {
		String sourceIndexName = indexNameProvider.indexName();
		String documentId = nextIdAsString();
		SampleEntity sampleEntity = SampleEntity.builder().id(documentId).message("abc").build();
		operations.save(sampleEntity);

		indexNameProvider.increment();
		String destIndexName = indexNameProvider.indexName();
		operations.indexOps(IndexCoordinates.of(destIndexName)).create();

		final ReindexRequest reindexRequest = ReindexRequest.builder(IndexCoordinates.of(sourceIndexName), IndexCoordinates.of(destIndexName))
				.withRefresh(true).build();
		final ReindexResponse reindex = operations.reindex(reindexRequest);
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(matchAllQuery()).build();
		assertThat(reindex.getTotal()).isEqualTo(1);
		assertThat(operations.count(searchQuery, IndexCoordinates.of(destIndexName))).isEqualTo(1);
	}

	@Test // #1529
	void shouldWorkSubmitReindexTask(){
		String sourceIndexName = indexNameProvider.indexName();
		indexNameProvider.increment();
		String destIndexName = indexNameProvider.indexName();
		operations.indexOps(IndexCoordinates.of(destIndexName)).create();
		final ReindexRequest reindexRequest = ReindexRequest
				.builder(IndexCoordinates.of(sourceIndexName), IndexCoordinates.of(destIndexName)).build();
		String task = operations.submitReindex(reindexRequest);
		// Maybe there should be a task api to detect whether the task exists
		assertThat(task).isNotBlank();
	}

	// region entities
	@Document(indexName = "#{@indexNameProvider.indexName()}")
	@Setting(shards = 1, replicas = 0, refreshInterval = "-1")
	static class SampleEntity {
		@Nullable
		@Id private String id;
		@Nullable
		@Field(type = Text, store = true, fielddata = true) private String type;
		@Nullable
		@Field(type = Text, store = true, fielddata = true) private String message;
		@Nullable private int rate;
		@Nullable
		@ScriptedField private Double scriptedRate;
		@Nullable private boolean available;
		@Nullable private GeoPoint location;
		@Nullable
		@Version private Long version;

		static Builder builder() {
			return new Builder();
		}

		static class Builder {

			@Nullable private String id;
			@Nullable private String type;
			@Nullable private String message;
			@Nullable private Long version;
			@Nullable private int rate;
			@Nullable private GeoPoint location;

			public Builder id(String id) {
				this.id = id;
				return this;
			}

			public Builder type(String type) {
				this.type = type;
				return this;
			}

			public Builder message(String message) {
				this.message = message;
				return this;
			}

			public Builder version(Long version) {
				this.version = version;
				return this;
			}

			public Builder rate(int rate) {
				this.rate = rate;
				return this;
			}

			public Builder location(GeoPoint location) {
				this.location = location;
				return this;
			}

			public SampleEntity build() {
				SampleEntity sampleEntity = new SampleEntity();
				sampleEntity.setId(id);
				sampleEntity.setType(type);
				sampleEntity.setMessage(message);
				sampleEntity.setRate(rate);
				sampleEntity.setVersion(version);
				sampleEntity.setLocation(location);
				return sampleEntity;
			}
		}

		public SampleEntity() {}

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getType() {
			return type;
		}

		public void setType(@Nullable String type) {
			this.type = type;
		}

		@Nullable
		public String getMessage() {
			return message;
		}

		public void setMessage(@Nullable String message) {
			this.message = message;
		}

		public int getRate() {
			return rate;
		}

		public void setRate(int rate) {
			this.rate = rate;
		}

		@Nullable
		public java.lang.Double getScriptedRate() {
			return scriptedRate;
		}

		public void setScriptedRate(@Nullable java.lang.Double scriptedRate) {
			this.scriptedRate = scriptedRate;
		}

		public boolean isAvailable() {
			return available;
		}

		public void setAvailable(boolean available) {
			this.available = available;
		}

		@Nullable
		public GeoPoint getLocation() {
			return location;
		}

		public void setLocation(@Nullable GeoPoint location) {
			this.location = location;
		}

		@Nullable
		public java.lang.Long getVersion() {
			return version;
		}

		public void setVersion(@Nullable java.lang.Long version) {
			this.version = version;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			SampleEntity that = (SampleEntity) o;

			if (rate != that.rate)
				return false;
			if (available != that.available)
				return false;
			if (id != null ? !id.equals(that.id) : that.id != null)
				return false;
			if (type != null ? !type.equals(that.type) : that.type != null)
				return false;
			if (message != null ? !message.equals(that.message) : that.message != null)
				return false;
			if (scriptedRate != null ? !scriptedRate.equals(that.scriptedRate) : that.scriptedRate != null)
				return false;
			if (location != null ? !location.equals(that.location) : that.location != null)
				return false;
			return version != null ? version.equals(that.version) : that.version == null;
		}

		@Override
		public int hashCode() {
			int result = id != null ? id.hashCode() : 0;
			result = 31 * result + (type != null ? type.hashCode() : 0);
			result = 31 * result + (message != null ? message.hashCode() : 0);
			result = 31 * result + rate;
			result = 31 * result + (scriptedRate != null ? scriptedRate.hashCode() : 0);
			result = 31 * result + (available ? 1 : 0);
			result = 31 * result + (location != null ? location.hashCode() : 0);
			result = 31 * result + (version != null ? version.hashCode() : 0);
			return result;
		}
	}

	@Document(indexName = "#{@indexNameProvider.indexName()}")
	private static class SampleEntityUUIDKeyed {
		@Nullable
		@Id private UUID id;
		@Nullable private String type;
		@Nullable
		@Field(type = FieldType.Text, fielddata = true) private String message;
		@Nullable private int rate;
		@Nullable
		@ScriptedField private Long scriptedRate;
		@Nullable private boolean available;
		@Nullable private GeoPoint location;
		@Nullable
		@Version private Long version;

		@Nullable
		public UUID getId() {
			return id;
		}

		public void setId(@Nullable UUID id) {
			this.id = id;
		}

		@Nullable
		public String getType() {
			return type;
		}

		public void setType(@Nullable String type) {
			this.type = type;
		}

		@Nullable
		public String getMessage() {
			return message;
		}

		public void setMessage(@Nullable String message) {
			this.message = message;
		}

		public int getRate() {
			return rate;
		}

		public void setRate(int rate) {
			this.rate = rate;
		}

		@Nullable
		public java.lang.Long getScriptedRate() {
			return scriptedRate;
		}

		public void setScriptedRate(@Nullable java.lang.Long scriptedRate) {
			this.scriptedRate = scriptedRate;
		}

		public boolean isAvailable() {
			return available;
		}

		public void setAvailable(boolean available) {
			this.available = available;
		}

		@Nullable
		public GeoPoint getLocation() {
			return location;
		}

		public void setLocation(@Nullable GeoPoint location) {
			this.location = location;
		}

		@Nullable
		public java.lang.Long getVersion() {
			return version;
		}

		public void setVersion(@Nullable java.lang.Long version) {
			this.version = version;
		}
	}

	@Document(indexName = "test-index-book-core-template")
	static class Book {
		@Nullable
		@Id private String id;
		@Nullable private String name;
		@Nullable
		@Field(type = FieldType.Object) private Author author;
		@Nullable
		@Field(type = FieldType.Nested) private Map<Integer, Collection<String>> buckets = new HashMap<>();
		@Nullable
		@MultiField(mainField = @Field(type = FieldType.Text, analyzer = "whitespace"),
				otherFields = { @InnerField(suffix = "prefix", type = FieldType.Text, analyzer = "stop",
						searchAnalyzer = "standard") }) private String description;

		public Book(@Nullable String id, @Nullable String name, @Nullable Author author,
				@Nullable Map<java.lang.Integer, Collection<String>> buckets, @Nullable String description) {
			this.id = id;
			this.name = name;
			this.author = author;
			this.buckets = buckets;
			this.description = description;
		}

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getName() {
			return name;
		}

		public void setName(@Nullable String name) {
			this.name = name;
		}

		@Nullable
		public Author getAuthor() {
			return author;
		}

		public void setAuthor(@Nullable Author author) {
			this.author = author;
		}

		@Nullable
		public Map<java.lang.Integer, Collection<String>> getBuckets() {
			return buckets;
		}

		public void setBuckets(@Nullable Map<java.lang.Integer, Collection<String>> buckets) {
			this.buckets = buckets;
		}

		@Nullable
		public String getDescription() {
			return description;
		}

		public void setDescription(@Nullable String description) {
			this.description = description;
		}

		static Builder builder() {
			return new Builder();
		}

		static class Builder {
			@Nullable private String id;
			@Nullable private String name;
			@Nullable private Author author;
			@Nullable private Map<Integer, Collection<String>> buckets = new HashMap<>();
			@Nullable private String description;

			public Builder id(@Nullable String id) {
				this.id = id;
				return this;
			}

			public Builder name(@Nullable String name) {
				this.name = name;
				return this;
			}

			public Builder author(@Nullable Author author) {
				this.author = author;
				return this;
			}

			public Builder buckets(@Nullable Map<java.lang.Integer, Collection<String>> buckets) {
				this.buckets = buckets;
				return this;
			}

			public Builder description(@Nullable String description) {
				this.description = description;
				return this;
			}

			Book build() {
				return new Book(id, name, author, buckets, description);
			}
		}
	}

	static class Author {
		@Nullable private String id;
		@Nullable private String name;

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getName() {
			return name;
		}

		public void setName(@Nullable String name) {
			this.name = name;
		}
	}

	@Document(indexName = "#{@indexNameProvider.indexName()}", versionType = EXTERNAL_GTE)
	private static class GTEVersionEntity {
		@Nullable
		@Version private Long version;
		@Nullable
		@Id private String id;
		@Nullable private String name;

		@Nullable
		public java.lang.Long getVersion() {
			return version;
		}

		public void setVersion(@Nullable java.lang.Long version) {
			this.version = version;
		}

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getName() {
			return name;
		}

		public void setName(@Nullable String name) {
			this.name = name;
		}
	}

	@Document(indexName = "test-index-hetro1-core-template")
	static class HetroEntity1 {
		@Nullable
		@Id private String id;
		@Nullable private String firstName;
		@Nullable
		@Version private Long version;

		HetroEntity1(String id, String firstName) {
			this.id = id;
			this.firstName = firstName;
			this.version = System.currentTimeMillis();
		}

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getFirstName() {
			return firstName;
		}

		public void setFirstName(@Nullable String firstName) {
			this.firstName = firstName;
		}

		@Nullable
		public java.lang.Long getVersion() {
			return version;
		}

		public void setVersion(@Nullable java.lang.Long version) {
			this.version = version;
		}
	}

	@Document(indexName = "test-index-hetro2-core-template")
	static class HetroEntity2 {

		@Nullable
		@Id private String id;
		@Nullable private String lastName;
		@Nullable
		@Version private Long version;

		HetroEntity2(String id, String lastName) {
			this.id = id;
			this.lastName = lastName;
			this.version = System.currentTimeMillis();
		}

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getLastName() {
			return lastName;
		}

		public void setLastName(@Nullable String lastName) {
			this.lastName = lastName;
		}

		@Nullable
		public java.lang.Long getVersion() {
			return version;
		}

		public void setVersion(@Nullable java.lang.Long version) {
			this.version = version;
		}
	}

	@Document(indexName = "test-index-server-configuration")
	@Setting(useServerConfiguration = true, shards = 10, replicas = 10, refreshInterval = "-1")
	private static class UseServerConfigurationEntity {

		@Nullable
		@Id private String id;
		@Nullable private String val;

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getVal() {
			return val;
		}

		public void setVal(@Nullable String val) {
			this.val = val;
		}
	}

	@Document(indexName = "test-index-sample-mapping")
	static class SampleMappingEntity {

		@Nullable
		@Id private String id;
		@Nullable
		@Field(type = Text, index = false, store = true, analyzer = "standard") private String message;

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getMessage() {
			return message;
		}

		public void setMessage(@Nullable String message) {
			this.message = message;
		}

		static class NestedEntity {

			@Nullable
			@Field(type = Text) private String someField;

			@Nullable
			public String getSomeField() {
				return someField;
			}

			public void setSomeField(String someField) {
				this.someField = someField;
			}
		}
	}

	@Document(indexName = "#{@indexNameProvider.indexName()}")
	static class SearchHitsEntity {
		@Nullable
		@Id private String id;
		@Nullable
		@Field(type = FieldType.Long) Long number;
		@Nullable
		@Field(type = FieldType.Keyword) String keyword;

		public SearchHitsEntity() {}

		public SearchHitsEntity(@Nullable String id, @Nullable java.lang.Long number, @Nullable String keyword) {
			this.id = id;
			this.number = number;
			this.keyword = keyword;
		}

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public java.lang.Long getNumber() {
			return number;
		}

		public void setNumber(@Nullable java.lang.Long number) {
			this.number = number;
		}

		@Nullable
		public String getKeyword() {
			return keyword;
		}

		public void setKeyword(@Nullable String keyword) {
			this.keyword = keyword;
		}
	}

	@Document(indexName = "test-index-highlight-entity-template")
	static class HighlightEntity {
		@Nullable
		@Id private String id;
		@Nullable private String message;

		public HighlightEntity(@Nullable String id, @Nullable String message) {
			this.id = id;
			this.message = message;
		}

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getMessage() {
			return message;
		}

		public void setMessage(@Nullable String message) {
			this.message = message;
		}
	}

	@Document(indexName = "test-index-optimistic-entity-template")
	static class OptimisticEntity {
		@Nullable
		@Id private String id;
		@Nullable private String message;
		@Nullable private SeqNoPrimaryTerm seqNoPrimaryTerm;

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getMessage() {
			return message;
		}

		public void setMessage(@Nullable String message) {
			this.message = message;
		}

		@Nullable
		public SeqNoPrimaryTerm getSeqNoPrimaryTerm() {
			return seqNoPrimaryTerm;
		}

		public void setSeqNoPrimaryTerm(@Nullable SeqNoPrimaryTerm seqNoPrimaryTerm) {
			this.seqNoPrimaryTerm = seqNoPrimaryTerm;
		}
	}

	@Document(indexName = "test-index-optimistic-and-versioned-entity-template")
	static class OptimisticAndVersionedEntity {
		@Nullable
		@Id private String id;
		@Nullable private String message;
		@Nullable private SeqNoPrimaryTerm seqNoPrimaryTerm;
		@Nullable
		@Version private Long version;

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public String getMessage() {
			return message;
		}

		public void setMessage(@Nullable String message) {
			this.message = message;
		}

		@Nullable
		public SeqNoPrimaryTerm getSeqNoPrimaryTerm() {
			return seqNoPrimaryTerm;
		}

		public void setSeqNoPrimaryTerm(@Nullable SeqNoPrimaryTerm seqNoPrimaryTerm) {
			this.seqNoPrimaryTerm = seqNoPrimaryTerm;
		}

		@Nullable
		public java.lang.Long getVersion() {
			return version;
		}

		public void setVersion(@Nullable java.lang.Long version) {
			this.version = version;
		}
	}

	@Document(indexName = "test-index-versioned-entity-template")
	static class VersionedEntity {
		@Nullable
		@Id private String id;
		@Nullable
		@Version private Long version;

		@Nullable
		public String getId() {
			return id;
		}

		public void setId(@Nullable String id) {
			this.id = id;
		}

		@Nullable
		public java.lang.Long getVersion() {
			return version;
		}

		public void setVersion(@Nullable java.lang.Long version) {
			this.version = version;
		}
	}

	@Document(indexName = "#{@indexNameProvider.indexName()}")
	static class SampleJoinEntity {
		@Nullable
		@Id
		@Field(type = Keyword) private String uuid;
		@Nullable
		@JoinTypeRelations(relations = {
				@JoinTypeRelation(parent = "question", children = { "answer" }) }) private JoinField<String> myJoinField;
		@Nullable
		@Field(type = Text) private String text;

		@Nullable
		public String getUuid() {
			return uuid;
		}

		public void setUuid(@Nullable String uuid) {
			this.uuid = uuid;
		}

		@Nullable
		public JoinField<String> getMyJoinField() {
			return myJoinField;
		}

		public void setMyJoinField(@Nullable JoinField<String> myJoinField) {
			this.myJoinField = myJoinField;
		}

		@Nullable
		public String getText() {
			return text;
		}

		public void setText(@Nullable String text) {
			this.text = text;
		}
	}

	@Document(indexName = "immutable-class")
	private static final class ImmutableEntity {
		@Id
		@Nullable private final String id;
		@Field(type = FieldType.Text) private final String text;
		@Nullable private final SeqNoPrimaryTerm seqNoPrimaryTerm;

		public ImmutableEntity(@Nullable String id, String text, @Nullable SeqNoPrimaryTerm seqNoPrimaryTerm) {
			this.id = id;
			this.text = text;
			this.seqNoPrimaryTerm = seqNoPrimaryTerm;
		}

		@Nullable
		public String getId() {
			return id;
		}

		public String getText() {
			return text;
		}

		@Nullable
		public SeqNoPrimaryTerm getSeqNoPrimaryTerm() {
			return seqNoPrimaryTerm;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			ImmutableEntity that = (ImmutableEntity) o;

			if (!id.equals(that.id))
				return false;
			if (!text.equals(that.text))
				return false;
			return seqNoPrimaryTerm != null ? seqNoPrimaryTerm.equals(that.seqNoPrimaryTerm) : that.seqNoPrimaryTerm == null;
		}

		@Override
		public int hashCode() {
			int result = id.hashCode();
			result = 31 * result + text.hashCode();
			result = 31 * result + (seqNoPrimaryTerm != null ? seqNoPrimaryTerm.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return "ImmutableEntity{" + "id='" + id + '\'' + ", text='" + text + '\'' + ", seqNoPrimaryTerm="
					+ seqNoPrimaryTerm + '}';
		}
	}

	@Document(indexName = "immutable-scripted")
	public static final class ImmutableWithScriptedEntity {
		@Id private final String id;
		@Field(type = Integer)
		@Nullable private final int rate;
		@Nullable
		@ScriptedField private final Double scriptedRate;

		public ImmutableWithScriptedEntity(String id, int rate, @Nullable java.lang.Double scriptedRate) {
			this.id = id;
			this.rate = rate;
			this.scriptedRate = scriptedRate;
		}

		public String getId() {
			return id;
		}

		public int getRate() {
			return rate;
		}

		@Nullable
		public Double getScriptedRate() {
			return scriptedRate;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			ImmutableWithScriptedEntity that = (ImmutableWithScriptedEntity) o;

			if (rate != that.rate)
				return false;
			if (!id.equals(that.id))
				return false;
			return scriptedRate != null ? scriptedRate.equals(that.scriptedRate) : that.scriptedRate == null;
		}

		@Override
		public int hashCode() {
			int result = id.hashCode();
			result = 31 * result + rate;
			result = 31 * result + (scriptedRate != null ? scriptedRate.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return "ImmutableWithScriptedEntity{" + "id='" + id + '\'' + ", rate=" + rate + ", scriptedRate=" + scriptedRate
					+ '}';
		}
	}
	// endregion
}
