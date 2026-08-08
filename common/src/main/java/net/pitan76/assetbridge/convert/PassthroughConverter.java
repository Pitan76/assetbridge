package net.pitan76.assetbridge.convert;

import net.pitan76.assetbridge.asset.AssetVersion;

/** Used for binary resources (textures) and anything the spec never changed. */
public final class PassthroughConverter implements AssetConverter {
    @Override
    public byte[] convert(String path, byte[] data, AssetVersion from) {
        return data;
    }
}
