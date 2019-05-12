package org.opentripplanner.routing.algorithm.raptor.router;

import com.conveyal.r5.otp2.RangeRaptorService;
import com.conveyal.r5.otp2.api.path.Path;
import com.conveyal.r5.otp2.api.request.Optimization;
import com.conveyal.r5.otp2.api.request.RangeRaptorProfile;
import com.conveyal.r5.otp2.api.request.RangeRaptorRequest;
import com.conveyal.r5.otp2.api.request.RequestBuilder;
import com.conveyal.r5.otp2.api.request.TuningParameters;
import com.conveyal.r5.otp2.api.transit.TransferLeg;
import com.conveyal.r5.otp2.api.transit.TransitDataProvider;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.model.Stop;
import org.opentripplanner.routing.algorithm.raptor.itinerary.ItineraryMapper;
import org.opentripplanner.routing.algorithm.raptor.router.street.AccessEgressRouter;
import org.opentripplanner.routing.algorithm.raptor.router.street.TransferToAccessEgressLegMapper;
import org.opentripplanner.routing.algorithm.raptor.transit.Transfer;
import org.opentripplanner.routing.algorithm.raptor.transit.TransitLayer;
import org.opentripplanner.routing.algorithm.raptor.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptor.transit.request.RaptorRoutingRequestTransitData;
import org.opentripplanner.routing.core.RoutingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Does a complete transit search, including access and egress legs.
 */
public class RaptorRouter {
    private final TransitDataProvider<TripSchedule> otpRRDataProvider;
    private final TransitLayer transitLayer;
    private static final Logger LOG = LoggerFactory.getLogger(RaptorRouter.class);

    private static final RangeRaptorService<TripSchedule> rangeRaptorService = new RangeRaptorService<>(
            // TODO - Load turning parameters from config file
            new TuningParameters() {}
    );

    //TODO Naming
    public RaptorRouter(RoutingRequest request, TransitLayer transitLayer) {
        double startTime = System.currentTimeMillis();
        this.otpRRDataProvider = new RaptorRoutingRequestTransitData(
                transitLayer, startDate(request),2, request.modes, request.walkSpeed
        );
        LOG.info("Filtering tripPatterns took {} ms", System.currentTimeMillis() - startTime);
        this.transitLayer = transitLayer;
    }

    public TripPlan route(RoutingRequest request) {

        /* Prepare access/egress transfers */

        double startTimeAccessEgress = System.currentTimeMillis();

        Map<Stop, Transfer> accessTransfers =
            AccessEgressRouter.streetSearch(request, false, Integer.MAX_VALUE);
        Map<Stop, Transfer> egressTransfers =
            AccessEgressRouter.streetSearch(request, true, Integer.MAX_VALUE);

        TransferToAccessEgressLegMapper accessEgressLegMapper = new TransferToAccessEgressLegMapper(transitLayer);

        Collection<TransferLeg> accessTimes = accessEgressLegMapper.map(accessTransfers, request.walkSpeed);
        Collection<TransferLeg> egressTimes = accessEgressLegMapper.map(egressTransfers, request.walkSpeed);

        LOG.info("Access/egress routing took {} ms", System.currentTimeMillis() - startTimeAccessEgress);

        /* Prepare transit search */

        double startTimeRouting = System.currentTimeMillis();

        // TODO Time zones
        int departureTime = Instant.ofEpochMilli(request.dateTime * 1000).atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay();

        // TODO Expose parameters
        // TODO Remove parameters from API
        RequestBuilder builder = new RequestBuilder();
        builder.profile(RangeRaptorProfile.MULTI_CRITERIA)
                .searchParams()
                .earliestDepartureTime(departureTime)
                .searchWindowInSeconds(40  * 60)
                .latestArrivalTime(departureTime + 12 * 60 * 60)
                .addAccessStops(accessTimes)
                .addEgressStops(egressTimes)
                .boardSlackInSeconds(request.boardSlack)
                .timetableEnabled(false);

        //TODO Check in combination with timetableEnabled
        builder.enableOptimization(Optimization.PARETO_CHECK_AGAINST_DESTINATION);

        RangeRaptorRequest rangeRaptorRequest = builder.build();

        /* Route transit */

        // We know this cast is correct because we have instantiated rangeRaptorService as RangeRaptorService<TripSchedule>
        @SuppressWarnings("unchecked")
        Collection<Path<TripSchedule>> paths = rangeRaptorService.route(rangeRaptorRequest, this.otpRRDataProvider);

        LOG.info("Main routing took {} ms", System.currentTimeMillis() - startTimeRouting);

        /* Create itineraries */

        double startItineraries = System.currentTimeMillis();

        ItineraryMapper itineraryMapper = new ItineraryMapper(transitLayer, request);

        List<Itinerary> itineraries = paths.stream()
                .map(p -> itineraryMapper.createItinerary(request, p, accessTransfers, egressTransfers))
                .collect(Collectors.toList());

        TripPlan tripPlan = itineraryMapper.createTripPlan(request, itineraries);

        LOG.info("Creating itineraries took {} ms", System.currentTimeMillis() - startItineraries);

        return tripPlan;
    }

    private LocalDate startDate(RoutingRequest request) {
        return request.getDateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
