package net.pitan76.assetbridge;

import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.archive.ArchiveScanner;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetSource;
import net.pitan76.assetbridge.asset.AssetVersion;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MemoryBenchmark {

    private static final int MOD_COUNT = 10;
    private static final int BLOCKS_PER_MOD = 100;
    private static final int TEXTURE_SIZE_BYTES = 50 * 1024; // 50 KB

    @Test
    public void runBenchmark() throws Exception {
        System.out.println("=== Memory Benchmark Start ===");
        
        // 1. 一時ディレクトリとテスト用ダミーMOD ZIPの生成
        Path tempDir = Files.createTempDirectory("assetbridge_benchmark_");
        Path modsDir = tempDir.resolve("mods").resolve("assetbridge");
        Files.createDirectories(modsDir);

        System.out.println("Generating dummy mods...");
        List<Path> zipFiles = new ArrayList<>();
        byte[] dummyPngBytes = new byte[TEXTURE_SIZE_BYTES];
        new Random().nextBytes(dummyPngBytes);

        for (int i = 0; i < MOD_COUNT; i++) {
            String modId = "mod_" + i;
            Path zipPath = modsDir.resolve(modId + ".jar");
            zipFiles.add(zipPath);

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                // pack.mcmeta
                zos.putNextEntry(new ZipEntry("pack.mcmeta"));
                String mcmeta = "{\"pack\":{\"pack_format\":8,\"description\":\"Dummy Pack\"}}";
                zos.write(mcmeta.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // Blocks
                for (int j = 0; j < BLOCKS_PER_MOD; j++) {
                    String blockName = "block_" + j;
                    
                    // blockstate
                    zos.putNextEntry(new ZipEntry("assets/" + modId + "/blockstates/" + blockName + ".json"));
                    String blockstate = "{\"variants\":{\"\":{\"model\":\"" + modId + ":block/" + blockName + "\"}}}";
                    zos.write(blockstate.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    // model
                    zos.putNextEntry(new ZipEntry("assets/" + modId + "/models/block/" + blockName + ".json"));
                    String model = "{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"" + modId + ":block/" + blockName + "\"}}";
                    zos.write(model.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    // texture (50KB)
                    zos.putNextEntry(new ZipEntry("assets/" + modId + "/textures/block/" + blockName + ".png"));
                    zos.write(dummyPngBytes);
                    zos.closeEntry();
                }
            }
        }

        int totalBlocks = MOD_COUNT * BLOCKS_PER_MOD;
        long totalTextureSize = (long) totalBlocks * TEXTURE_SIZE_BYTES;
        System.out.printf("Generated %d mods containing %d total blocks (total texture data: %.2f MB)%n", 
                MOD_COUNT, totalBlocks, totalTextureSize / (1024.0 * 1024.0));

        // GCを実行してメモリ使用量を安定させる
        runGC();
        long memBeforeScan = getUsedMemory();
        System.out.printf("Memory before scan: %.2f MB%n", memBeforeScan / (1024.0 * 1024.0));

        // 2. スキャンとビルドを実行
        long startTime = System.currentTimeMillis();
        List<AssetArchive> archives = ArchiveScanner.scan(tempDir);
        BridgedAssetManager assets = AssetPipeline.build(archives, ns -> false);
        long endTime = System.currentTimeMillis();

        runGC();
        long memAfterBuild = getUsedMemory();
        long buildDiff = memAfterBuild - memBeforeScan;

        System.out.printf("Scan & Build took: %d ms%n", (endTime - startTime));
        System.out.printf("Memory after scan & build: %.2f MB (Diff: +%.2f MB)%n", 
                memAfterBuild / (1024.0 * 1024.0), buildDiff / (1024.0 * 1024.0));
        System.out.printf("Bundle holds %d blocks and %d resources%n", assets.blocks().size(), assets.resources().size());

        // 3. テクスチャをメモリ上に全ロードするシミュレーション（遅延ロードしなかった場合のシミュレーション）
        System.out.println("Simulating full texture load (reading all resources)...");
        long readStartTime = System.currentTimeMillis();
        long totalBytesRead = 0;
        List<byte[]> loadedData = new ArrayList<>();
        for (Map.Entry<AssetPath, AssetSource> entry : assets.resources().entrySet()) {
            byte[] bytes = entry.getValue().readAll();
            loadedData.add(bytes);
            totalBytesRead += bytes.length;
        }
        long readEndTime = System.currentTimeMillis();

        runGC();
        long memAfterLoad = getUsedMemory();
        long loadDiff = memAfterLoad - memAfterBuild;

        System.out.printf("Full read took: %d ms (Read: %.2f MB)%n", 
                (readEndTime - readStartTime), totalBytesRead / (1024.0 * 1024.0));
        System.out.printf("Memory after full load: %.2f MB (Diff from build: +%.2f MB)%n", 
                memAfterLoad / (1024.0 * 1024.0), loadDiff / (1024.0 * 1024.0));

        // 4. クローズ処理
        for (AssetArchive archive : archives) {
            archive.close();
        }

        // 一時ファイルの削除
        for (Path file : zipFiles) {
            Files.deleteIfExists(file);
        }
        Files.deleteIfExists(modsDir);
        Files.deleteIfExists(modsDir.getParent());
        Files.deleteIfExists(tempDir);

        System.out.println("=== Memory Benchmark End ===");
    }

    private static void runGC() {
        for (int i = 0; i < 5; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }
    }

    private static long getUsedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
