package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor._data.api.PathUtils.join;
import static org.opentripplanner.raptor._data.api.PathUtils.pathsToString;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.SECONDS_IN_DAY;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.free;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.walk;
import static org.opentripplanner.raptor._data.transit.TestRoute.route;
import static org.opentripplanner.raptor._data.transit.TestTripSchedule.schedule;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.TC_STANDARD;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.multiCriteria;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.framework.time.TimeUtils;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.RaptorTestConstants;
import org.opentripplanner.raptor._data.api.PathUtils;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorProfile;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.raptor.moduletests.support.RaptorModuleTestCase;

/*
 * FEATURE UNDER TEST
 *
 * Raptor should take into account opening hours/time restrictions on access. If the time
 * restrictions require it, there should be a wait before boarding the trip so that the access is
 * traversed while "open".
 */
public class G01_AccessWithOpeningHoursTest implements RaptorTestConstants {

  private static final int T00_18 = TimeUtils.time("00:18");
  public static final int T00_20 = TimeUtils.time("00:20");

  private final TestTransitData data = new TestTransitData();
  private final RaptorRequestBuilder<TestTripSchedule> requestBuilder = new RaptorRequestBuilder<>();
  private final RaptorService<TestTripSchedule> raptorService = new RaptorService<>(
    RaptorConfig.defaultConfigForTest()
  );

  @BeforeEach
  public void setup() {
    data.withRoute(
      route("R1", STOP_A, STOP_B, STOP_C, STOP_D, STOP_E)
        .withTimetable(
          schedule("00:10 00:15 00:20 00:25 00:30"),
          schedule("00:15 00:20 00:25 00:30 00:35"),
          schedule("00:20 00:25 00:30 00:35 00:40"),
          schedule("00:25 00:30 00:35 00:40 00:45"),
          schedule("24:10 24:15 24:20 24:25 24:30"),
          schedule("24:15 24:20 24:25 24:30 24:35"),
          schedule("24:20 24:25 24:30 24:35 24:40"),
          schedule("24:25 24:30 24:35 24:40 24:45")
        )
    );
    requestBuilder.searchParams().addEgressPaths(walk(STOP_E, D1m));

    requestBuilder
      .searchParams()
      .earliestDepartureTime(T00_10)
      .searchWindow(Duration.ofMinutes(30))
      .timetable(true);

    ModuleTestDebugLogging.setupDebugLogging(data, requestBuilder);
  }

  static List<RaptorModuleTestCase> openInWholeSearchIntervalTestCases() {
    var expected = new String[] {
      "Walk 2m ~ B ~ BUS R1 0:15 0:30 ~ E ~ Walk 1m [0:13 0:31 18m 0tx $1860]",
      "Walk 2m ~ B ~ BUS R1 0:20 0:35 ~ E ~ Walk 1m [0:18 0:36 18m 0tx $1860]",
      "Walk 2m ~ B ~ BUS R1 0:25 0:40 ~ E ~ Walk 1m [0:23 0:41 18m 0tx $1860]",
      "Walk 2m ~ B ~ BUS R1 0:30 0:45 ~ E ~ Walk 1m [0:28 0:46 18m 0tx $1860]",
      "Walk 2m ~ B ~ BUS R1 0:15+1d 0:30+1d ~ E ~ Walk 1m [0:13+1d 0:31+1d 18m 0tx $1860]",
    };

    return RaptorModuleTestCase
      .of()
      .add(TC_STANDARD, PathUtils.withoutCost(expected))
      .add(multiCriteria(), expected)
      .build();
  }

  @ParameterizedTest
  @MethodSource("openInWholeSearchIntervalTestCases")
  void openInWholeSearchIntervalTest(RaptorModuleTestCase testCase) {
    requestBuilder.searchParams().addAccessPaths(walk(STOP_B, D2m));

    var request = testCase.withConfig(requestBuilder);
    var response = raptorService.route(request, data);
    assertEquals(testCase.expected(), pathsToString(response));
  }

  /*
   * There is no time restriction, all routes are found.
   */
  @Test
  public void openInWholeSearchIntervalTest() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .addAccessPaths(walk(STOP_B, D2m));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m ~ B ~ BUS R1 0:15 0:30 ~ E ~ Walk 1m [0:13 0:31 18m 0tx $1860]",
        "Walk 2m ~ B ~ BUS R1 0:20 0:35 ~ E ~ Walk 1m [0:18 0:36 18m 0tx $1860]",
        "Walk 2m ~ B ~ BUS R1 0:25 0:40 ~ E ~ Walk 1m [0:23 0:41 18m 0tx $1860]",
        "Walk 2m ~ B ~ BUS R1 0:30 0:45 ~ E ~ Walk 1m [0:28 0:46 18m 0tx $1860]",
        "Walk 2m ~ B ~ BUS R1 0:15+1d 0:30+1d ~ E ~ Walk 1m [0:13+1d 0:31+1d 18m 0tx $1860]"
      ),
      pathsToString(response)
    );
  }

  @Test
  public void openInWholeSearchIntervalTestFullDay() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .searchWindow(Duration.ofDays(2))
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_00, T01_00));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:15 0:30 ~ E ~ Walk 1m [0:13 0:31 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:20 0:35 ~ E ~ Walk 1m [0:18 0:36 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:25 0:40 ~ E ~ Walk 1m [0:23 0:41 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:30 0:45 ~ E ~ Walk 1m [0:28 0:46 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:15+1d 0:30+1d ~ E ~ Walk 1m [0:13+1d 0:31+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:20+1d 0:35+1d ~ E ~ Walk 1m [0:18+1d 0:36+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:25+1d 0:40+1d ~ E ~ Walk 1m [0:23+1d 0:41+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:30+1d 0:45+1d ~ E ~ Walk 1m [0:28+1d 0:46+1d 18m 0tx $1860]"
      ),
      pathsToString(response)
    );
  }

  @Test
  public void openInWholeSearchIntervalTestNextDay() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .earliestDepartureTime(SECONDS_IN_DAY + T00_10)
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_00, T01_00));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:15+1d 0:30+1d ~ E ~ Walk 1m [0:13+1d 0:31+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:20+1d 0:35+1d ~ E ~ Walk 1m [0:18+1d 0:36+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:25+1d 0:40+1d ~ E ~ Walk 1m [0:23+1d 0:41+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 1:00) ~ B ~ BUS R1 0:30+1d 0:45+1d ~ E ~ Walk 1m [0:28+1d 0:46+1d 18m 0tx $1860]"
      ),
      pathsToString(response)
    );
  }

  /*
   * The access is only open after 00:18, which means that we may arrive at the stop at 00:20 at
   * the earliest.
   */
  @Test
  public void openInSecondHalfIntervalTest() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_18, T01_00));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:20 0:35 ~ E ~ Walk 1m [0:18 0:36 18m 0tx $1860]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:25 0:40 ~ E ~ Walk 1m [0:23 0:41 18m 0tx $1860]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:30 0:45 ~ E ~ Walk 1m [0:28 0:46 18m 0tx $1860]",
        // This bus may only be reached by waiting at the stop between 01:00 and 24:15:00,
        // since the access only opens at 0:18, which is too late to catch the bus.
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:15+1d 0:30+1d ~ E ~ Walk 1m [1:00 0:31+1d 23h31m 0tx $85440]"
      ),
      pathsToString(response)
    );
  }

  @Test
  public void openInSecondHalfIntervalTestFullDay() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .searchWindow(Duration.ofDays(2))
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_18, T01_00));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:20 0:35 ~ E ~ Walk 1m [0:18 0:36 18m 0tx $1860]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:25 0:40 ~ E ~ Walk 1m [0:23 0:41 18m 0tx $1860]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:30 0:45 ~ E ~ Walk 1m [0:28 0:46 18m 0tx $1860]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:15+1d 0:30+1d ~ E ~ Walk 1m [1:00 0:31+1d 23h31m 0tx $85440]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:20+1d 0:35+1d ~ E ~ Walk 1m [0:18+1d 0:36+1d 18m 0tx $1860]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:25+1d 0:40+1d ~ E ~ Walk 1m [0:23+1d 0:41+1d 18m 0tx $1860]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:30+1d 0:45+1d ~ E ~ Walk 1m [0:28+1d 0:46+1d 18m 0tx $1860]"
      ),
      pathsToString(response)
    );
  }

  @Test
  public void openInSecondHalfIntervalTestNextDay() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .earliestDepartureTime(SECONDS_IN_DAY + T00_10)
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_18, T01_00));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:20+1d 0:35+1d ~ E ~ Walk 1m [0:18+1d 0:36+1d 18m 0tx $1860]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:25+1d 0:40+1d ~ E ~ Walk 1m [0:23+1d 0:41+1d 18m 0tx $1860]",
        "Walk 2m Open(0:18 1:00) ~ B ~ BUS R1 0:30+1d 0:45+1d ~ E ~ Walk 1m [0:28+1d 0:46+1d 18m 0tx $1860]"
      ),
      pathsToString(response)
    );
  }

  /*
   * The access is only open before 00:20, which means that we arrive at the stop by 00:22 at the latest.
   */
  @Test
  public void openInFirstHalfIntervalTest() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_00, T00_20));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:15 0:30 ~ E ~ Walk 1m [0:13 0:31 18m 0tx $1860]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:20 0:35 ~ E ~ Walk 1m [0:18 0:36 18m 0tx $1860]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:25 0:40 ~ E ~ Walk 1m [0:20 0:41 21m 0tx $2040]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:15+1d 0:30+1d ~ E ~ Walk 1m [0:13+1d 0:31+1d 18m 0tx $1860]"
      ),
      pathsToString(response)
    );
  }

  @Test
  public void openInFirstHalfIntervalTestFullDay() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .searchWindow(Duration.ofDays(2))
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_00, T00_20));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:15 0:30 ~ E ~ Walk 1m [0:13 0:31 18m 0tx $1860]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:20 0:35 ~ E ~ Walk 1m [0:18 0:36 18m 0tx $1860]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:25 0:40 ~ E ~ Walk 1m [0:20 0:41 21m 0tx $2040]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:15+1d 0:30+1d ~ E ~ Walk 1m [0:13+1d 0:31+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:20+1d 0:35+1d ~ E ~ Walk 1m [0:18+1d 0:36+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:25+1d 0:40+1d ~ E ~ Walk 1m [0:20+1d 0:41+1d 21m 0tx $2040]"
      ),
      pathsToString(response)
    );
  }

  @Test
  public void openInFirstHalfIntervalTestNextDay() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .earliestDepartureTime(SECONDS_IN_DAY + T00_10)
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_00, T00_20));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:15+1d 0:30+1d ~ E ~ Walk 1m [0:13+1d 0:31+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:20+1d 0:35+1d ~ E ~ Walk 1m [0:18+1d 0:36+1d 18m 0tx $1860]",
        "Walk 2m Open(0:00 0:20) ~ B ~ BUS R1 0:25+1d 0:40+1d ~ E ~ Walk 1m [0:20+1d 0:41+1d 21m 0tx $2040]"
      ),
      pathsToString(response)
    );
  }

  /*
   * The access is only open after 00:18 and before 00:20. This means that we arrive at the stop at
   * 00:20 at the earliest and 00:22 at the latest.
   */
  @Test
  public void partiallyOpenIntervalTest() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_18, T00_20));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:18 0:20) ~ B ~ BUS R1 0:20 0:35 ~ E ~ Walk 1m [0:18 0:36 18m 0tx $1860]",
        "Walk 2m Open(0:18 0:20) ~ B ~ BUS R1 0:25 0:40 ~ E ~ Walk 1m [0:20 0:41 21m 0tx $2040]",
        "Walk 2m Open(0:18 0:20) ~ B ~ BUS R1 0:20+1d 0:35+1d ~ E ~ Walk 1m [0:18+1d 0:36+1d 18m 0tx $1860]"
      ),
      pathsToString(response)
    );
  }

  @Test
  public void partiallyOpenIntervalTestFullDay() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .searchWindow(Duration.ofDays(2))
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_18, T00_20));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:18 0:20) ~ B ~ BUS R1 0:20 0:35 ~ E ~ Walk 1m [0:18 0:36 18m 0tx $1860]",
        "Walk 2m Open(0:18 0:20) ~ B ~ BUS R1 0:25 0:40 ~ E ~ Walk 1m [0:20 0:41 21m 0tx $2040]",
        "Walk 2m Open(0:18 0:20) ~ B ~ BUS R1 0:20+1d 0:35+1d ~ E ~ Walk 1m [0:18+1d 0:36+1d 18m 0tx $1860]",
        "Walk 2m Open(0:18 0:20) ~ B ~ BUS R1 0:25+1d 0:40+1d ~ E ~ Walk 1m [0:20+1d 0:41+1d 21m 0tx $2040]"
      ),
      pathsToString(response)
    );
  }

  @Test
  public void partiallyOpenIntervalTestNextDay() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .earliestDepartureTime(SECONDS_IN_DAY + T00_10)
      .addAccessPaths(walk(STOP_B, D2m).openingHours(T00_18, T00_20));

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(
      join(
        "Walk 2m Open(0:18 0:20) ~ B ~ BUS R1 0:20+1d 0:35+1d ~ E ~ Walk 1m [0:18+1d 0:36+1d 18m 0tx $1860]",
        "Walk 2m Open(0:18 0:20) ~ B ~ BUS R1 0:25+1d 0:40+1d ~ E ~ Walk 1m [0:20+1d 0:41+1d 21m 0tx $2040]"
      ),
      pathsToString(response)
    );
  }

  @Test
  public void closed() {
    requestBuilder
      .profile(RaptorProfile.MULTI_CRITERIA)
      .searchParams()
      .searchWindow(Duration.ofDays(2))
      .addAccessPaths(free(STOP_B).openingHoursClosed());

    var response = raptorService.route(requestBuilder.build(), data);

    assertEquals(0, response.paths().size());
  }
}
