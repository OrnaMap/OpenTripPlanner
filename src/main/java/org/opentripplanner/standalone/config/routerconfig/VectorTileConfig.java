package org.opentripplanner.standalone.config.routerconfig;

import static org.opentripplanner.inspector.vector.LayerParameters.CACHE_MAX_SECONDS;
import static org.opentripplanner.inspector.vector.LayerParameters.EXPANSION_FACTOR;
import static org.opentripplanner.inspector.vector.LayerParameters.MAX_ZOOM;
import static org.opentripplanner.inspector.vector.LayerParameters.MIN_ZOOM;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_0;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_1;
import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_5;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.ext.vectortiles.VectorTilesResource;
import org.opentripplanner.inspector.vector.LayerParameters;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.standalone.config.framework.json.ParameterBuilder;

public class VectorTileConfig
  implements VectorTilesResource.LayersParameters<VectorTilesResource.LayerType> {

  public static final VectorTileConfig DEFAULT = new VectorTileConfig(List.of(), null);
  private final List<LayerParameters<VectorTilesResource.LayerType>> layers;

  @Nullable
  private final String basePath;

  VectorTileConfig(
    Collection<? extends LayerParameters<VectorTilesResource.LayerType>> layers,
    @Nullable String basePath
  ) {
    this.layers = List.copyOf(layers);
    this.basePath = basePath;
  }

  @Override
  public List<LayerParameters<VectorTilesResource.LayerType>> layers() {
    return layers;
  }

  public Optional<String> basePath() {
    return Optional.ofNullable(basePath);
  }

  public static VectorTileConfig mapVectorTilesParameters(NodeAdapter node, String paramName) {
    var root = node.of(paramName).asObject();
    return new VectorTileConfig(
      root
        .of("layers")
        .since(V2_0)
        .summary("Configuration of the individual layers for the Mapbox vector tiles.")
        .asObjects(VectorTileConfig::mapLayer),
      root.of("basePath").since(V2_5).asString(null)
    );
  }

  public static Layer mapLayer(NodeAdapter node) {
    return new Layer(
      node
        .of("name")
        .since(V2_0)
        .summary("Used in the url to fetch tiles, and as the layer name in the vector tiles.")
        .asString(),
      node
        .of("type")
        .since(V2_0)
        .summary("Type of the layer.")
        .asEnum(VectorTilesResource.LayerType.class),
      node
        .of("mapper")
        .since(V2_0)
        .summary(
          "Describes the mapper converting from the OTP model entities to the vector tile properties."
        )
        .description("Currently `Digitransit` is supported for all layer types.")
        .asString(),
      node
        .of("maxZoom")
        .since(V2_0)
        .summary("Maximum zoom levels the layer is active for.")
        .asInt(MAX_ZOOM),
      node
        .of("minZoom")
        .since(V2_0)
        .summary("Minimum zoom levels the layer is active for.")
        .asInt(MIN_ZOOM),
      node
        .of("cacheMaxSeconds")
        .since(V2_0)
        .summary("Sets the cache header in the response.")
        .description("The lowest value of the layers included is selected.")
        .asInt(CACHE_MAX_SECONDS),
      node
        .of("expansionFactor")
        .since(V2_0)
        .summary("How far outside its boundaries should the tile contain information.")
        .description(
          "The value is a fraction of the tile size. If you are having problem with icons and " +
          "shapes being clipped at tile edges, then increase this number."
        )
        .asDouble(EXPANSION_FACTOR)
    );
  }

  record Layer(
    String name,
    VectorTilesResource.LayerType type,
    String mapper,
    int maxZoom,
    int minZoom,
    int cacheMaxSeconds,
    double expansionFactor
  )
    implements LayerParameters<VectorTilesResource.LayerType> {}
}
