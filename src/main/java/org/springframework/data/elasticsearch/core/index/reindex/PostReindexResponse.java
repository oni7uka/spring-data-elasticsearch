package org.springframework.data.elasticsearch.core.index.reindex;

import org.springframework.lang.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Response of reindex request.
 * (@see https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html#docs-reindex-api-response-body)
 *
 * @author Onizuka
 */
public class PostReindexResponse {

	private final long took;
	private final boolean timedOut;
	private final long total;
	private final long updated;
	private final long deleted;
	private final int batches;
	private final long versionConflicts;
	private final long noops;
	private final long bulkRetries;
	private final long searchRetries;
	private final long throttledMillis;
	private final double requestsPerSecond;
	private final long throttledUntilMillis;
	private final List<Failure> failures;

	private PostReindexResponse(long took, boolean timedOut, long total, long updated, long deleted, int batches,
								long versionConflicts, long noops, long bulkRetries, long searchRetries,
								long throttledMillis, double requestsPerSecond, long throttledUntilMillis, List<Failure> failures) {
		this.took = took;
		this.timedOut = timedOut;
		this.total = total;
		this.updated = updated;
		this.deleted = deleted;
		this.batches = batches;
		this.versionConflicts = versionConflicts;
		this.noops = noops;
		this.bulkRetries = bulkRetries;
		this.searchRetries = searchRetries;
		this.throttledMillis = throttledMillis;
		this.requestsPerSecond = requestsPerSecond;
		this.throttledUntilMillis = throttledUntilMillis;
		this.failures = failures;
	}

	/**
	 * The number of milliseconds from start to end of the whole operation.
	 */
	public long getTook() {
		return took;
	}

	/**
	 * Did any of the sub-requests that were part of this request timeout?
	 */
	public boolean isTimedOut() {
		return timedOut;
	}

	/**
	 * The number of documents that were successfully processed.
	 */
	public long getTotal() {
		return total;
	}

	/**
	 * The number of documents that were successfully updated.
	 */
	public long getUpdated() {
		return updated;
	}

	/**
	 * The number of documents that were successfully deleted.
	 */
	public long getDeleted() {
		return deleted;
	}

	/**
	 * The number of scroll responses pulled back by the update by query.
	 */
	public int getBatches() {
		return batches;
	}

	/**
	 * The number of version conflicts that the update by query hit.
	 */
	public long getVersionConflicts() {
		return versionConflicts;
	}

	/**
	 * The number of documents that were ignored because the script used for the update by query returned a noop value for
	 * ctx.op.
	 */
	public long getNoops() {
		return noops;
	}

	/**
	 * The number of times that the request had retry bulk actions.
	 */
	public long getBulkRetries() {
		return bulkRetries;
	}

	/**
	 * The number of times that the request had retry search actions.
	 */
	public long getSearchRetries() {
		return searchRetries;
	}

	/**
	 * Number of milliseconds the request slept to conform to requests_per_second.
	 */
	public long getThrottledMillis() {
		return throttledMillis;
	}

	/**
	 *  The number of requests per second effectively executed during the reindex.
	 */
	public double getRequestsPerSecond() {
		return requestsPerSecond;
	}

	/**
	 * This field should always be equal to zero in a _reindex response.
	 * It only has meaning when using the Task API, where it indicates the next time (in milliseconds since epoch)
	 * a throttled request will be executed again in order to conform to requests_per_second.
	 */
	public long getThrottledUntilMillis() {
		return throttledUntilMillis;
	}

	/**
	 * All of the bulk failures. Version conflicts are only included if the request sets abortOnVersionConflict to true
	 * (the default).
	 */
	public List<Failure> getFailures() {
		return failures;
	}

	/**
	 * Create a new {@link PostReindexResponse} to build {@link PostReindexResponse}
	 *
	 * @return a new {@link PostReindexResponse} to build {@link PostReindexResponse}
	 */
	public static PostReindexResponseBuilder builder() {
		return new PostReindexResponseBuilder();
	}

	public static class Failure {

		@Nullable private final String index;
		@Nullable private final String type;
		@Nullable private final String id;
		@Nullable private final Exception cause;
		@Nullable private final Integer status;
		@Nullable private final Long seqNo;
		@Nullable private final Long term;
		@Nullable private final Boolean aborted;

		private Failure(@Nullable String index, @Nullable String type, @Nullable String id, @Nullable Exception cause,
						@Nullable Integer status, @Nullable Long seqNo, @Nullable Long term, @Nullable Boolean aborted) {
			this.index = index;
			this.type = type;
			this.id = id;
			this.cause = cause;
			this.status = status;
			this.seqNo = seqNo;
			this.term = term;
			this.aborted = aborted;
		}

		@Nullable
		public String getIndex() {
			return index;
		}

		@Nullable
		public String getType() {
			return type;
		}

		@Nullable
		public String getId() {
			return id;
		}

		@Nullable
		public Exception getCause() {
			return cause;
		}

		@Nullable
		public Integer getStatus() {
			return status;
		}

		@Nullable
		public Long getSeqNo() {
			return seqNo;
		}

		@Nullable
		public Long getTerm() {
			return term;
		}

		@Nullable
		public Boolean getAborted() {
			return aborted;
		}

		/**
		 * Create a new {@link Failure.FailureBuilder} to build {@link Failure}
		 *
		 * @return a new {@link Failure.FailureBuilder} to build {@link Failure}
		 */
		public static Failure.FailureBuilder builder() {
			return new Failure.FailureBuilder();
		}

		/**
		 * Builder for {@link Failure}
		 */
		public static final class FailureBuilder {
			@Nullable private String index;
			@Nullable private String type;
			@Nullable private String id;
			@Nullable private Exception cause;
			@Nullable private Integer status;
			@Nullable private Long seqNo;
			@Nullable private Long term;
			@Nullable private Boolean aborted;

			private FailureBuilder() {}

			public Failure.FailureBuilder withIndex(String index) {
				this.index = index;
				return this;
			}

			public Failure.FailureBuilder withType(String type) {
				this.type = type;
				return this;
			}

			public Failure.FailureBuilder withId(String id) {
				this.id = id;
				return this;
			}

			public Failure.FailureBuilder withCause(Exception cause) {
				this.cause = cause;
				return this;
			}

			public Failure.FailureBuilder withStatus(Integer status) {
				this.status = status;
				return this;
			}

			public Failure.FailureBuilder withSeqNo(Long seqNo) {
				this.seqNo = seqNo;
				return this;
			}

			public Failure.FailureBuilder withTerm(Long term) {
				this.term = term;
				return this;
			}

			public Failure.FailureBuilder withAborted(Boolean aborted) {
				this.aborted = aborted;
				return this;
			}

			public Failure build() {
				return new Failure(index, type, id, cause, status, seqNo, term, aborted);
			}
		}
	}

	public static final class PostReindexResponseBuilder {
		private long took;
		private boolean timedOut;
		private long total;
		private long updated;
		private long deleted;
		private int batches;
		private long versionConflicts;
		private long noops;
		private long bulkRetries;
		private long searchRetries;
		private long throttledMillis;
		private double requestsPerSecond;
		private long throttledUntilMillis;
		private List<Failure> failures = Collections.emptyList();

		private PostReindexResponseBuilder() {}

		public PostReindexResponseBuilder withTook(long took) {
			this.took = took;
			return this;
		}

		public PostReindexResponseBuilder withTimedOut(boolean timedOut) {
			this.timedOut = timedOut;
			return this;
		}

		public PostReindexResponseBuilder withTotal(long total) {
			this.total = total;
			return this;
		}

		public PostReindexResponseBuilder withUpdated(long updated) {
			this.updated = updated;
			return this;
		}

		public PostReindexResponseBuilder withDeleted(long deleted) {
			this.deleted = deleted;
			return this;
		}

		public PostReindexResponseBuilder withBatches(int batches) {
			this.batches = batches;
			return this;
		}

		public PostReindexResponseBuilder withVersionConflicts(long versionConflicts) {
			this.versionConflicts = versionConflicts;
			return this;
		}

		public PostReindexResponseBuilder withNoops(long noops) {
			this.noops = noops;
			return this;
		}

		public PostReindexResponseBuilder withBulkRetries(long bulkRetries) {
			this.bulkRetries = bulkRetries;
			return this;
		}

		public PostReindexResponseBuilder withSearchRetries(long searchRetries) {
			this.searchRetries = searchRetries;
			return this;
		}

		public PostReindexResponseBuilder withThrottledMillis(long throttledMillis){
			this.throttledMillis = throttledMillis;
			return this;
		}

		public PostReindexResponseBuilder withRequestsPerSecond(double requestsPerSecond){
			this.requestsPerSecond = requestsPerSecond;
			return this;
		}

		public PostReindexResponseBuilder withThrottledUntilMillis(long throttledUntilMillis){
			this.throttledUntilMillis = throttledUntilMillis;
			return this;
		}

		public PostReindexResponseBuilder withFailures(List<Failure> failures) {
			this.failures = failures;
			return this;
		}

		public PostReindexResponse build() {
			return new PostReindexResponse(took, timedOut, total, updated, deleted, batches, versionConflicts, noops, bulkRetries,
					searchRetries, throttledMillis, requestsPerSecond, throttledUntilMillis, failures);
		}
	}
}
