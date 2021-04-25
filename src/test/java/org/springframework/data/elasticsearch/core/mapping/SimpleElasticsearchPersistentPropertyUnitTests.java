/*
 * Copyright 2018-2021 the original author or authors.
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
package org.springframework.data.elasticsearch.core.mapping;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.model.FieldNamingStrategy;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.mapping.model.SnakeCaseFieldNamingStrategy;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

/**
 * Unit tests for {@link SimpleElasticsearchPersistentProperty}.
 *
 * @author Oliver Gierke
 * @author Peter-Josef Meisch
 * @author Roman Puchkovskiy
 * @author Sascha Woo
 */
public class SimpleElasticsearchPersistentPropertyUnitTests {

	private final SimpleElasticsearchMappingContext context = new SimpleElasticsearchMappingContext();

	@Test // DATAES-562
	public void fieldAnnotationWithNameSetsFieldname() {

		SimpleElasticsearchPersistentEntity<?> persistentEntity = context
				.getRequiredPersistentEntity(FieldNameProperty.class);
		ElasticsearchPersistentProperty persistentProperty = persistentEntity.getPersistentProperty("fieldProperty");

		assertThat(persistentProperty).isNotNull();
		assertThat(persistentProperty.getFieldName()).isEqualTo("by-name");
	}

	@Test // DATAES-562
	public void fieldAnnotationWithValueSetsFieldname() {

		SimpleElasticsearchPersistentEntity<?> persistentEntity = context
				.getRequiredPersistentEntity(FieldValueProperty.class);
		ElasticsearchPersistentProperty persistentProperty = persistentEntity.getPersistentProperty("fieldProperty");

		assertThat(persistentProperty).isNotNull();
		assertThat(persistentProperty.getFieldName()).isEqualTo("by-value");
	}

	@Test // DATAES-896
	void shouldUseNameFromMultiFieldMainField() {
		SimpleElasticsearchPersistentEntity<?> persistentEntity = context
				.getRequiredPersistentEntity(MultiFieldProperty.class);
		ElasticsearchPersistentProperty persistentProperty = persistentEntity.getPersistentProperty("mainfieldProperty");

		assertThat(persistentProperty).isNotNull();
		assertThat(persistentProperty.getFieldName()).isEqualTo("mainfield");
	}

	@Test // DATAES-716, DATAES-792, DATAES-924
	void shouldSetPropertyConverters() {
		SimpleElasticsearchPersistentEntity<?> persistentEntity = context.getRequiredPersistentEntity(DatesProperty.class);

		ElasticsearchPersistentProperty persistentProperty = persistentEntity.getRequiredPersistentProperty("localDate");
		assertThat(persistentProperty.hasPropertyConverter()).isTrue();
		assertThat(persistentProperty.getPropertyConverter()).isNotNull();

		persistentProperty = persistentEntity.getRequiredPersistentProperty("localDateTime");
		assertThat(persistentProperty.hasPropertyConverter()).isTrue();
		assertThat(persistentProperty.getPropertyConverter()).isNotNull();

		persistentProperty = persistentEntity.getRequiredPersistentProperty("legacyDate");
		assertThat(persistentProperty.hasPropertyConverter()).isTrue();
		assertThat(persistentProperty.getPropertyConverter()).isNotNull();

		persistentProperty = persistentEntity.getRequiredPersistentProperty("localDateList");
		assertThat(persistentProperty.hasPropertyConverter()).isTrue();
		assertThat(persistentProperty.getPropertyConverter()).isNotNull();
	}

	@Test // DATAES-716
	void shouldConvertFromLocalDate() {
		SimpleElasticsearchPersistentEntity<?> persistentEntity = context.getRequiredPersistentEntity(DatesProperty.class);
		ElasticsearchPersistentProperty persistentProperty = persistentEntity.getRequiredPersistentProperty("localDate");
		LocalDate localDate = LocalDate.of(2019, 12, 27);

		String converted = persistentProperty.getPropertyConverter().write(localDate);

		assertThat(converted).isEqualTo("27.12.2019");
	}

	@Test // DATAES-716
	void shouldConvertToLocalDate() {
		SimpleElasticsearchPersistentEntity<?> persistentEntity = context.getRequiredPersistentEntity(DatesProperty.class);
		ElasticsearchPersistentProperty persistentProperty = persistentEntity.getRequiredPersistentProperty("localDate");

		Object converted = persistentProperty.getPropertyConverter().read("27.12.2019");

		assertThat(converted).isInstanceOf(LocalDate.class);
		assertThat(converted).isEqualTo(LocalDate.of(2019, 12, 27));
	}

	@Test // DATAES_792
	void shouldConvertFromLegacyDate() {
		SimpleElasticsearchPersistentEntity<?> persistentEntity = context.getRequiredPersistentEntity(DatesProperty.class);
		ElasticsearchPersistentProperty persistentProperty = persistentEntity.getRequiredPersistentProperty("legacyDate");
		GregorianCalendar calendar = GregorianCalendar
				.from(ZonedDateTime.of(LocalDateTime.of(2020, 4, 19, 19, 44), ZoneId.of("UTC")));
		Date legacyDate = calendar.getTime();

		String converted = persistentProperty.getPropertyConverter().write(legacyDate);

		assertThat(converted).isEqualTo("20200419T194400.000Z");
	}

	@Test // DATAES-792
	void shouldConvertToLegacyDate() {
		SimpleElasticsearchPersistentEntity<?> persistentEntity = context.getRequiredPersistentEntity(DatesProperty.class);
		ElasticsearchPersistentProperty persistentProperty = persistentEntity.getRequiredPersistentProperty("legacyDate");

		Object converted = persistentProperty.getPropertyConverter().read("20200419T194400.000Z");

		assertThat(converted).isInstanceOf(Date.class);
		GregorianCalendar calendar = GregorianCalendar
				.from(ZonedDateTime.of(LocalDateTime.of(2020, 4, 19, 19, 44), ZoneId.of("UTC")));
		Date legacyDate = calendar.getTime();
		assertThat(converted).isEqualTo(legacyDate);
	}

	@Test // DATAES-799
	void shouldReportSeqNoPrimaryTermPropertyWhenTheTypeIsSeqNoPrimaryTerm() {
		SimpleElasticsearchPersistentEntity<?> entity = context.getRequiredPersistentEntity(SeqNoPrimaryTermProperty.class);
		ElasticsearchPersistentProperty seqNoProperty = entity.getRequiredPersistentProperty("seqNoPrimaryTerm");

		assertThat(seqNoProperty.isSeqNoPrimaryTermProperty()).isTrue();
	}

	@Test // DATAES-799
	void shouldNotReportSeqNoPrimaryTermPropertyWhenTheTypeIsNotSeqNoPrimaryTerm() {
		SimpleElasticsearchPersistentEntity<?> entity = context.getRequiredPersistentEntity(SeqNoPrimaryTermProperty.class);
		ElasticsearchPersistentProperty stringProperty = entity.getRequiredPersistentProperty("string");

		assertThat(stringProperty.isSeqNoPrimaryTermProperty()).isFalse();
	}

	@Test // DATAES-799
	void seqNoPrimaryTermPropertyShouldNotBeWritable() {
		SimpleElasticsearchPersistentEntity<?> entity = context.getRequiredPersistentEntity(SeqNoPrimaryTermProperty.class);
		ElasticsearchPersistentProperty seqNoProperty = entity.getRequiredPersistentProperty("seqNoPrimaryTerm");

		assertThat(seqNoProperty.isWritable()).isFalse();
	}

	@Test // DATAES-799
	void seqNoPrimaryTermPropertyShouldNotBeReadable() {
		SimpleElasticsearchPersistentEntity<?> entity = context.getRequiredPersistentEntity(SeqNoPrimaryTermProperty.class);
		ElasticsearchPersistentProperty seqNoProperty = entity.getRequiredPersistentProperty("seqNoPrimaryTerm");

		assertThat(seqNoProperty.isReadable()).isFalse();
	}

	@Test // DATAES-924
	@DisplayName("should require pattern for custom date format")
	void shouldRequirePatternForCustomDateFormat() {
		assertThatExceptionOfType(MappingException.class) //
				.isThrownBy(() -> context.getRequiredPersistentEntity(DateFieldWithCustomFormatAndNoPattern.class)) //
				.withMessageContaining("pattern");
	}

	@Test // #1565
	@DisplayName("should use default FieldNamingStrategy")
	void shouldUseDefaultFieldNamingStrategy() {

		SimpleElasticsearchPersistentEntity.ContextConfiguration contextConfiguration = new SimpleElasticsearchPersistentEntity.ContextConfiguration(
				PropertyNameFieldNamingStrategy.INSTANCE, true);

		ElasticsearchPersistentEntity<FieldNamingStrategyEntity> entity = new SimpleElasticsearchPersistentEntity<>(
				ClassTypeInformation.from(FieldNamingStrategyEntity.class), contextConfiguration);
		ClassTypeInformation<FieldNamingStrategyEntity> type = ClassTypeInformation.from(FieldNamingStrategyEntity.class);

		java.lang.reflect.Field field = ReflectionUtils.findField(FieldNamingStrategyEntity.class,
				"withoutCustomFieldName");
		SimpleElasticsearchPersistentProperty property = new SimpleElasticsearchPersistentProperty(Property.of(type, field),
				entity, SimpleTypeHolder.DEFAULT);

		assertThat(property.getFieldName()).isEqualTo("withoutCustomFieldName");

		field = ReflectionUtils.findField(FieldNamingStrategyEntity.class, "withCustomFieldName");
		property = new SimpleElasticsearchPersistentProperty(Property.of(type, field), entity, SimpleTypeHolder.DEFAULT);

		assertThat(property.getFieldName()).isEqualTo("CUStomFIEldnAME");
	}

	@Test // #1565
	@DisplayName("should use custom FieldNamingStrategy")
	void shouldUseCustomFieldNamingStrategy() {

		FieldNamingStrategy fieldNamingStrategy = new SnakeCaseFieldNamingStrategy();
		SimpleElasticsearchPersistentEntity.ContextConfiguration contextConfiguration = new SimpleElasticsearchPersistentEntity.ContextConfiguration(
				fieldNamingStrategy, true);

		ElasticsearchPersistentEntity<FieldNamingStrategyEntity> entity = new SimpleElasticsearchPersistentEntity<>(
				ClassTypeInformation.from(FieldNamingStrategyEntity.class), contextConfiguration);
		ClassTypeInformation<FieldNamingStrategyEntity> type = ClassTypeInformation.from(FieldNamingStrategyEntity.class);

		java.lang.reflect.Field field = ReflectionUtils.findField(FieldNamingStrategyEntity.class,
				"withoutCustomFieldName");
		SimpleElasticsearchPersistentProperty property = new SimpleElasticsearchPersistentProperty(Property.of(type, field),
				entity, SimpleTypeHolder.DEFAULT);

		assertThat(property.getFieldName()).isEqualTo("without_custom_field_name");

		field = ReflectionUtils.findField(FieldNamingStrategyEntity.class, "withCustomFieldName");
		property = new SimpleElasticsearchPersistentProperty(Property.of(type, field), entity, SimpleTypeHolder.DEFAULT);

		assertThat(property.getFieldName()).isEqualTo("CUStomFIEldnAME");
	}

	// region entities
	static class FieldNameProperty {
		@Nullable @Field(name = "by-name") String fieldProperty;
	}

	static class FieldValueProperty {
		@Nullable @Field(value = "by-value") String fieldProperty;
	}

	static class MultiFieldProperty {
		@Nullable @MultiField(mainField = @Field("mainfield"),
				otherFields = { @InnerField(suffix = "suff", type = FieldType.Keyword) }) String mainfieldProperty;
	}

	static class DatesProperty {
		@Nullable @Field(type = FieldType.Date, format = {}, pattern = "dd.MM.uuuu") LocalDate localDate;
		@Nullable @Field(type = FieldType.Date, format = DateFormat.basic_date_time) LocalDateTime localDateTime;
		@Nullable @Field(type = FieldType.Date, format = DateFormat.basic_date_time) Date legacyDate;
		@Nullable @Field(type = FieldType.Date, format = {}, pattern = "dd.MM.uuuu") List<LocalDate> localDateList;
	}

	static class SeqNoPrimaryTermProperty {
		@Nullable private SeqNoPrimaryTerm seqNoPrimaryTerm;
		@Nullable private String string;

		@Nullable
		public SeqNoPrimaryTerm getSeqNoPrimaryTerm() {
			return seqNoPrimaryTerm;
		}

		public void setSeqNoPrimaryTerm(@Nullable SeqNoPrimaryTerm seqNoPrimaryTerm) {
			this.seqNoPrimaryTerm = seqNoPrimaryTerm;
		}

		@Nullable
		public String getString() {
			return string;
		}

		public void setString(@Nullable String string) {
			this.string = string;
		}
	}

	static class DateFieldWithCustomFormatAndNoPattern {
		@Nullable private @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "") LocalDateTime datetime;

		@Nullable
		public LocalDateTime getDatetime() {
			return datetime;
		}

		public void setDatetime(@Nullable LocalDateTime datetime) {
			this.datetime = datetime;
		}
	}

	static class FieldNamingStrategyEntity {
		@Nullable private String withoutCustomFieldName;
		@Field(name = "CUStomFIEldnAME") private String withCustomFieldName;

		@Nullable
		public String getWithoutCustomFieldName() {
			return withoutCustomFieldName;
		}

		public void setWithoutCustomFieldName(@Nullable String withoutCustomFieldName) {
			this.withoutCustomFieldName = withoutCustomFieldName;
		}

		public String getWithCustomFieldName() {
			return withCustomFieldName;
		}

		public void setWithCustomFieldName(String withCustomFieldName) {
			this.withCustomFieldName = withCustomFieldName;
		}
	}
	// endregion
}
