package net.pitan76.assetbridge.convert;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;

/** Used for binary resources (textures) and anything the spec never changed. */
public class PassthroughConverter implements AssetConverter {
    @Override
    public byte[] convert(AssetPath path, byte[] data, AssetVersion from) {
        return data;
    }
}
