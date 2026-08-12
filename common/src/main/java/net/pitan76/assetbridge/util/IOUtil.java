package net.pitan76.assetbridge.util;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

public class IOUtil {
    public static byte[] readAllBytes(InputStream in) throws IOException {
        //? if >=1.17 {
        return in.readAllBytes();
        //? } else {
        /*
        return IOUtils.toByteArray(in);
        *///? }
    }
}
