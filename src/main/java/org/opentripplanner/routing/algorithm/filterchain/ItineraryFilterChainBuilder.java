package org.opentripplanner.routing.algorithm.filterchain;

import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.filterchain.FilterChainParameters.GroupBySimilarity;
import org.opentripplanner.routing.algorithm.filterchain.filters.DebugFilterWrapper;
import org.opentripplanner.routing.algorithm.filterchain.filters.FilterChain;
import org.opentripplanner.routing.algorithm.filterchain.filters.GroupBySimilarLegsFilter;
import org.opentripplanner.routing.algorithm.filterchain.filters.LatestDepartureTimeFilter;
import org.opentripplanner.routing.algorithm.filterchain.filters.MaxLimitFilter;
import org.opentripplanner.routing.algorithm.filterchain.filters.OtpDefaultSortOrder;
import org.opentripplanner.routing.algorithm.filterchain.filters.RemoveTransitIfStreetOnlyIsBetterFilter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;


/**
 * Create a filter chain based on the given config.
 */
public class ItineraryFilterChainBuilder {
    private final FilterChainParameters parameters;
    private Instant latestDepartureTimeLimit = null;
    private Consumer<Itinerary> maxLimitReachedSubscriber;


    public ItineraryFilterChainBuilder(FilterChainParameters parameters) {
        this.parameters = parameters;
    }

    /**
     * Max departure time. This is a absolute filter on the itinerary departure time from the
     * origin.
     */
    public ItineraryFilterChainBuilder withLatestDepartureTimeLimit(Instant latestDepartureTimeLimit) {
        this.latestDepartureTimeLimit = latestDepartureTimeLimit;
        return this;
    }

    /**
     * If the maximum number of itineraries is exceeded, then the excess itineraries are removed.
     * To get notified about this a subscriber can be added. The first itinerary removed by the
     * {@code maxLimit} is retuned. The 'maxLimit' check is last thing happening in the
     * filter-chain after the final sort. So, if another filter remove an itinerary, the
     * itinerary is not considered with the respect to this feature.
     *
     * @param maxLimitReachedSubscriber the subscriber to notify in case any elements are removed.
     *                                  Only the first element removed is passed to the subscriber.
     */
    public ItineraryFilterChainBuilder withMaxLimitReachedSubscriber(Consumer<Itinerary> maxLimitReachedSubscriber) {
        this.maxLimitReachedSubscriber = maxLimitReachedSubscriber;
        return this;
    }

    public ItineraryFilter build() {
        List<ItineraryFilter> filters = new ArrayList<>();

        // Sort list on {@code groupByP} in ascending order to keep as many of the elements in the
        // groups where the grouping parameter is relaxed as possible.
        {
            List<GroupBySimilarity> groupBy = parameters
                .groupBySimilarity()
                .stream()
                .sorted(Comparator.comparingDouble(o -> o.groupByP))
                .collect(Collectors.toList());

            for (GroupBySimilarity it : groupBy) {
                filters.add(new GroupBySimilarLegsFilter(it.groupByP, it.approximateMinLimit));
            }
        }

        // Remove itineraries if max limit is set
        if (parameters.maxNumberOfItineraries() > 0) {
            // Sort first to make sure we keep the most relevant itineraries
            filters.add(new OtpDefaultSortOrder(parameters.arriveBy()));
            filters.add(
                new MaxLimitFilter(
                    "number-of-itineraries-filter",
                    parameters.maxNumberOfItineraries(),
                    maxLimitReachedSubscriber
                )
            );
        }

        // Apply all absolute filters AFTER the groupBy filters. Absolute filters are filters that
        // remove elements/ based on the given itinerary properties - not considering other
        // itineraries. This may remove itineraries in the "groupBy" filters that are considered
        // worse than the itineraries removed here. Let take an example, 2 itineraries, A and B, are
        // returned. A have a significant higher cost than B, but share the same long last transit
        // leg. B depart AFTER the latest-departure-time (this may happen if the access is
        // time-shifted). Then, A will be removed by the "group-by" filters(similar to B, but cost
        // is worse). B is removed by the {@link LatestDepartureTimeFilter} below. This is exactly
        // what we want, since both itineraries are none optimal.
        {
            if (parameters.removeTransitWithHigherCostThanBestOnStreetOnly()) {
                filters.add(new RemoveTransitIfStreetOnlyIsBetterFilter());
            }

            if (latestDepartureTimeLimit != null) {
                filters.add(new LatestDepartureTimeFilter(latestDepartureTimeLimit));
            }
        }

        // Do the final itineraries sort
        filters.add(new OtpDefaultSortOrder(parameters.arriveBy()));

        if(parameters.debug()) {
            filters = addDebugWrappers(filters);
        }

        return new FilterChain(filters);
    }


    /* private methods */

    private List<ItineraryFilter> addDebugWrappers(List<ItineraryFilter> filters) {
        final DebugFilterWrapper.Factory factory = new DebugFilterWrapper.Factory();
        return filters.stream().map(factory::wrap).collect(Collectors.toList());
    }
}
