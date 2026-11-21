package dev.kizuna.meridian.piglin.server;

import dev.kizuna.meridian.human.server.HumanContent;
import dev.kizuna.meridian.protocol.AddonProtobuf;
import dev.kizuna.meridian.server.AddonRegistry;
import dev.kizuna.meridian.protocol.pb.AddonMessages.*;
import dev.kizuna.meridian.protocol.pb.BodyMessages.*;

/** 猪灵身体内容包，沿用人体构型并追加金囊占位器官。 */
public final class PiglinContent {
    public static final String NAMESPACE = "meridian_piglin";
    public static final String TEMPLATE_ID = "meridian_piglin:piglin";
    public static final int CONTENT_VERSION = 1;
    public static final String GOLD_POUCH = "gold_pouch";
    public static final String GOLD_POUCH_KIND = "meridian_piglin:gold_pouch";

    private static final double GOLD_POUCH_HP = 10;
    private static final double GOLD_POUCH_CAPACITY = 100;
    private static final double GOLD_POUCH_THROUGHPUT = 1;

    private PiglinContent() {
        // 模板是可重复生成的不可变消息，实例状态由身体宿主管理。
    }

    public static AddonBundle bundle() {
        var human = HumanContent.bundle();
        var humanReference = reference(HumanContent.TEMPLATE_ID, HumanContent.CONTENT_VERSION);
        var adult = human.getTemplatesList().stream()
                .filter(template -> template.getReference().equals(humanReference))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("人体内容包缺少指定版本的成人模板"));
        var resultBuilder = AddonBundle.newBuilder();
        resultBuilder.setSchemaVersion(human.getSchemaVersion());
        resultBuilder.setManifest(manifest(human.getManifest()));
        resultBuilder.addSymbols(goldPouchKind());
        resultBuilder.addTemplates(piglin(adult));
        var result = resultBuilder.build();
        AddonProtobuf.validateBundle(result);
        return result;
    }

    private static AddonManifest manifest(AddonManifest human) {
        var versionsBuilder = ContentVersionRange.newBuilder();
        versionsBuilder.setMinimum(HumanContent.CONTENT_VERSION);
        versionsBuilder.setMaximum(HumanContent.CONTENT_VERSION);
        var dependencyBuilder = AddonDependency.newBuilder();
        dependencyBuilder.setNamespace(HumanContent.NAMESPACE);
        dependencyBuilder.setVersions(versionsBuilder.build());
        var manifestBuilder = AddonManifest.newBuilder();
        manifestBuilder.setNamespace(NAMESPACE);
        manifestBuilder.setContentVersion(CONTENT_VERSION);
        manifestBuilder.setApiVersions(human.getApiVersions());
        manifestBuilder.addAllSupportedHosts(human.getSupportedHostsList());
        manifestBuilder.addRequiredCapabilities(AddonRegistry.BODY_TEMPLATES);
        manifestBuilder.addDependencies(dependencyBuilder.build());
        return manifestBuilder.build();
    }

    private static ContentSymbol goldPouchKind() {
        var symbolBuilder = ContentSymbol.newBuilder();
        symbolBuilder.setId(GOLD_POUCH_KIND);
        symbolBuilder.setKind(ContentSymbolKind.ORGAN_KIND);
        return symbolBuilder.build();
    }

    private static BodyTemplate piglin(BodyTemplate adult) {
        var structureBuilder = adult.getStructure().toBuilder();
        structureBuilder.addOrgans(goldPouch());
        var templateBuilder = adult.toBuilder();
        templateBuilder.setReference(reference(TEMPLATE_ID, CONTENT_VERSION));
        templateBuilder.setStructure(structureBuilder.build());
        return templateBuilder.build();
    }

    private static OrganDefinition goldPouch() {
        var builder = OrganDefinition.newBuilder();
        builder.setId(GOLD_POUCH);
        builder.setKind(GOLD_POUCH_KIND);
        builder.setDisplayName("金囊（暂存）");
        builder.setPartId("abdomen");
        builder.setMaxHp(GOLD_POUCH_HP);
        builder.setVolumeCapacity(GOLD_POUCH_CAPACITY);
        builder.setThroughputPerSecond(GOLD_POUCH_THROUGHPUT);
        builder.addPorts(port(GOLD_POUCH + ".in", PortDirection.INPUT));
        builder.addPorts(port(GOLD_POUCH + ".out", PortDirection.OUTPUT));
        builder.addTags("meridian_piglin:gold_pouch_placeholder");
        return builder.build();
    }

    private static Port port(String id, PortDirection direction) {
        var builder = Port.newBuilder();
        builder.setId(id);
        builder.setDirection(direction);
        return builder.build();
    }

    private static TemplateReference reference(String id, int version) {
        var builder = TemplateReference.newBuilder();
        builder.setId(id);
        builder.setVersion(version);
        return builder.build();
    }
}
