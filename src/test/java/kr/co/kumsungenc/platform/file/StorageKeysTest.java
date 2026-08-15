package kr.co.kumsungenc.platform.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StorageKeysTest {
    @Test
    void createsStableKeysForExistingDatabaseNames() {
        assertThat(StorageKeys.quoteAttachment("KS-20260811-ABC", "a.pdf"))
            .isEqualTo("KS-20260811-ABC/a.pdf");
        assertThat(StorageKeys.quoteDocument("KS-20260811-ABC", "estimate.pdf"))
            .isEqualTo("documents/KS-20260811-ABC/estimate.pdf");
        assertThat(StorageKeys.shopAttachment("SHOP-20260811-ABC", "drawing.dwg"))
            .isEqualTo("shop/SHOP-20260811-ABC/drawing.dwg");
    }

    @Test
    void rejectsTraversalAndAbsoluteKeys() {
        assertThatThrownBy(() -> StorageKeys.normalize("../secret"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageKeys.normalize("/absolute/file"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageKeys.normalize("safe\\..\\secret"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
