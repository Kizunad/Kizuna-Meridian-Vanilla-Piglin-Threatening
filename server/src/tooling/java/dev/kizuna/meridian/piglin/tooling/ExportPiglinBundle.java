package dev.kizuna.meridian.piglin.tooling;

import dev.kizuna.meridian.piglin.server.PiglinContent;
import dev.kizuna.meridian.protocol.AddonProtobuf;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 供功法目录校验绑定时读取猪灵身体模板。 */
public final class ExportPiglinBundle {
    private ExportPiglinBundle() {
    }

    public static void main(String[] args) throws IOException {
        var output = Path.of(args[0]).resolve("META-INF/kizuna_meridian/addons/meridian_piglin.pb");
        Files.createDirectories(output.getParent());
        Files.write(output, AddonProtobuf.encodeBundle(PiglinContent.bundle()));
    }
}
