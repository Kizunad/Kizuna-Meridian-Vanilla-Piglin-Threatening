package dev.kizuna.meridian.piglin.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.kizuna.meridian.protocol.AddonProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganDefinition;
import org.junit.Test;

public final class PiglinContentTest {
    @Test
    public void publishesHumanDerivedTemplateAndGoldPouch() {
        var bundle = PiglinContent.bundle();
        AddonProtobuf.validateBundle(bundle);
        assertEquals(PiglinContent.NAMESPACE, bundle.getManifest().getNamespace());
        assertEquals(PiglinContent.TEMPLATE_ID, bundle.getTemplates(0).getReference().getId());
        OrganDefinition pouch = bundle.getTemplates(0).getStructure().getOrgansList().stream()
                .filter(organ -> organ.getId().equals(PiglinContent.GOLD_POUCH)).findFirst().orElseThrow();
        assertEquals("金囊", pouch.getDisplayName());
        assertEquals(PiglinContent.GOLD_POUCH_KIND, pouch.getKind());
        assertTrue(pouch.getTagsList().contains("meridian_piglin:gold_pouch"));
    }
}
