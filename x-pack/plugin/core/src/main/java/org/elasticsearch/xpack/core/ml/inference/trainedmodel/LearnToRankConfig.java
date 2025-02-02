/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ml.inference.trainedmodel;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xpack.core.ml.MlConfigVersion;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.ltr.LearnToRankFeatureExtractorBuilder;
import org.elasticsearch.xpack.core.ml.inference.trainedmodel.ltr.QueryExtractorBuilder;
import org.elasticsearch.xpack.core.ml.utils.NamedXContentObjectHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class LearnToRankConfig extends RegressionConfig implements Rewriteable<LearnToRankConfig> {

    public static final ParseField NAME = new ParseField("learn_to_rank");
    static final TransportVersion MIN_SUPPORTED_TRANSPORT_VERSION = TransportVersion.current();
    public static final ParseField NUM_TOP_FEATURE_IMPORTANCE_VALUES = new ParseField("num_top_feature_importance_values");
    public static final ParseField FEATURE_EXTRACTORS = new ParseField("feature_extractors");
    public static final ParseField DEFAULT_PARAMS = new ParseField("default_params");

    public static LearnToRankConfig EMPTY_PARAMS = new LearnToRankConfig(null, null, null);

    private static final ObjectParser<LearnToRankConfig.Builder, Boolean> LENIENT_PARSER = createParser(true);
    private static final ObjectParser<LearnToRankConfig.Builder, Boolean> STRICT_PARSER = createParser(false);

    private static ObjectParser<LearnToRankConfig.Builder, Boolean> createParser(boolean lenient) {
        ObjectParser<LearnToRankConfig.Builder, Boolean> parser = new ObjectParser<>(
            NAME.getPreferredName(),
            lenient,
            LearnToRankConfig.Builder::new
        );
        parser.declareInt(Builder::setNumTopFeatureImportanceValues, NUM_TOP_FEATURE_IMPORTANCE_VALUES);
        parser.declareNamedObjects(
            Builder::setLearnToRankFeatureExtractorBuilders,
            (p, c, n) -> p.namedObject(LearnToRankFeatureExtractorBuilder.class, n, lenient),
            b -> {},
            FEATURE_EXTRACTORS
        );
        parser.declareObject(Builder::setParamsDefaults, (p, c) -> p.map(), DEFAULT_PARAMS);
        return parser;
    }

    public static LearnToRankConfig fromXContentStrict(XContentParser parser) {
        return STRICT_PARSER.apply(parser, null).build();
    }

    public static LearnToRankConfig fromXContentLenient(XContentParser parser) {
        return LENIENT_PARSER.apply(parser, null).build();
    }

    public static Builder builder(LearnToRankConfig config) {
        return new Builder(config);
    }

    private final List<LearnToRankFeatureExtractorBuilder> featureExtractorBuilders;
    private final Map<String, Object> paramsDefaults;

    public LearnToRankConfig(
        Integer numTopFeatureImportanceValues,
        List<LearnToRankFeatureExtractorBuilder> featureExtractorBuilders,
        Map<String, Object> paramsDefaults
    ) {
        super(DEFAULT_RESULTS_FIELD, numTopFeatureImportanceValues);
        if (featureExtractorBuilders != null) {
            Set<String> featureNames = featureExtractorBuilders.stream()
                .map(LearnToRankFeatureExtractorBuilder::featureName)
                .collect(Collectors.toSet());
            if (featureNames.size() < featureExtractorBuilders.size()) {
                throw new IllegalArgumentException(
                    "[" + FEATURE_EXTRACTORS.getPreferredName() + "] contains duplicate [feature_name] values"
                );
            }
        }
        this.featureExtractorBuilders = Collections.unmodifiableList(Objects.requireNonNullElse(featureExtractorBuilders, List.of()));
        this.paramsDefaults = Collections.unmodifiableMap(Objects.requireNonNullElse(paramsDefaults, Map.of()));
    }

    public LearnToRankConfig(StreamInput in) throws IOException {
        super(in);
        this.featureExtractorBuilders = in.readNamedWriteableCollectionAsList(LearnToRankFeatureExtractorBuilder.class);
        this.paramsDefaults = in.readMap();
    }

    public List<LearnToRankFeatureExtractorBuilder> getFeatureExtractorBuilders() {
        return featureExtractorBuilders;
    }

    public List<QueryExtractorBuilder> getQueryFeatureExtractorBuilders() {
        List<QueryExtractorBuilder> queryExtractorBuilders = new ArrayList<>();
        for (LearnToRankFeatureExtractorBuilder featureExtractorBuilder : featureExtractorBuilders) {
            if (featureExtractorBuilder instanceof QueryExtractorBuilder queryExtractorBuilder) {
                queryExtractorBuilders.add(queryExtractorBuilder);
            }
        }

        return queryExtractorBuilders;
    }

    @Override
    public String getResultsField() {
        return DEFAULT_RESULTS_FIELD;
    }

    public Map<String, Object> getParamsDefaults() {
        return paramsDefaults;
    }

    @Override
    public boolean isAllocateOnly() {
        return false;
    }

    @Override
    public boolean supportsIngestPipeline() {
        return false;
    }

    @Override
    public boolean supportsPipelineAggregation() {
        return false;
    }

    @Override
    public boolean supportsSearchRescorer() {
        return true;
    }

    @Override
    public String getWriteableName() {
        return NAME.getPreferredName();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeNamedWriteableCollection(featureExtractorBuilders);
        out.writeGenericMap(paramsDefaults);
    }

    @Override
    public String getName() {
        return NAME.getPreferredName();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NUM_TOP_FEATURE_IMPORTANCE_VALUES.getPreferredName(), getNumTopFeatureImportanceValues());
        if (featureExtractorBuilders.isEmpty() == false) {
            NamedXContentObjectHelper.writeNamedObjects(
                builder,
                params,
                true,
                FEATURE_EXTRACTORS.getPreferredName(),
                featureExtractorBuilders
            );
        }

        if (paramsDefaults.isEmpty() == false) {
            builder.field(DEFAULT_PARAMS.getPreferredName(), paramsDefaults);
        }

        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (super.equals(o) == false) return false;
        LearnToRankConfig that = (LearnToRankConfig) o;
        return Objects.equals(featureExtractorBuilders, that.featureExtractorBuilders)
            && Objects.equals(paramsDefaults, that.paramsDefaults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), featureExtractorBuilders, paramsDefaults);
    }

    @Override
    public final String toString() {
        return Strings.toString(this);
    }

    @Override
    public boolean isTargetTypeSupported(TargetType targetType) {
        return TargetType.REGRESSION.equals(targetType);
    }

    @Override
    public MlConfigVersion getMinimalSupportedMlConfigVersion() {
        return MlConfigVersion.CURRENT;
    }

    @Override
    public TransportVersion getMinimalSupportedTransportVersion() {
        return MIN_SUPPORTED_TRANSPORT_VERSION;
    }

    @Override
    public LearnToRankConfig rewrite(QueryRewriteContext ctx) throws IOException {
        if (this.featureExtractorBuilders.isEmpty()) {
            return this;
        }
        boolean rewritten = false;
        List<LearnToRankFeatureExtractorBuilder> rewrittenExtractors = new ArrayList<>(this.featureExtractorBuilders.size());
        for (LearnToRankFeatureExtractorBuilder extractorBuilder : this.featureExtractorBuilders) {
            LearnToRankFeatureExtractorBuilder rewrittenExtractor = Rewriteable.rewrite(extractorBuilder, ctx);
            rewrittenExtractors.add(rewrittenExtractor);
            rewritten |= (rewrittenExtractor != extractorBuilder);
        }
        if (rewritten) {
            return new LearnToRankConfig(getNumTopFeatureImportanceValues(), rewrittenExtractors, paramsDefaults);
        }
        return this;
    }

    public static class Builder {
        private Integer numTopFeatureImportanceValues;
        private List<LearnToRankFeatureExtractorBuilder> learnToRankFeatureExtractorBuilders;
        private Map<String, Object> paramsDefaults = Map.of();

        Builder() {}

        Builder(LearnToRankConfig config) {
            this.numTopFeatureImportanceValues = config.getNumTopFeatureImportanceValues();
            this.learnToRankFeatureExtractorBuilders = config.featureExtractorBuilders;
            this.paramsDefaults = config.getParamsDefaults();
        }

        public Builder setNumTopFeatureImportanceValues(Integer numTopFeatureImportanceValues) {
            this.numTopFeatureImportanceValues = numTopFeatureImportanceValues;
            return this;
        }

        public Builder setLearnToRankFeatureExtractorBuilders(
            List<LearnToRankFeatureExtractorBuilder> learnToRankFeatureExtractorBuilders
        ) {
            this.learnToRankFeatureExtractorBuilders = learnToRankFeatureExtractorBuilders;
            return this;
        }

        public Builder setParamsDefaults(Map<String, Object> paramsDefaults) {
            this.paramsDefaults = paramsDefaults;
            return this;
        }

        public LearnToRankConfig build() {
            return new LearnToRankConfig(numTopFeatureImportanceValues, learnToRankFeatureExtractorBuilders, paramsDefaults);
        }
    }
}
