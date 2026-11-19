package com.wf.wflib.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PhotoBookTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static PhotoEntry photo(UUID owner, long time) {
        return new PhotoEntry(UUID.randomUUID(), owner, "p", time, "minecraft:overworld", 1, 2, 3, 4f, 5f, 100, false);
    }

    @Test
    void quotaIsPerOwner() {
        PhotoBook book = new PhotoBook();
        assertTrue(book.add(photo(A, 1), 2));
        assertTrue(book.add(photo(A, 2), 2));
        assertFalse(book.add(photo(A, 3), 2));
        assertTrue(book.add(photo(B, 4), 2));
        assertEquals(2, book.count(A));
    }

    @Test
    void listsNewestFirstAndOnlyOwn() {
        PhotoBook book = new PhotoBook();
        PhotoEntry old = photo(A, 1);
        PhotoEntry fresh = photo(A, 9);
        book.add(old, 10);
        book.add(fresh, 10);
        book.add(photo(B, 5), 10);
        assertEquals(java.util.List.of(fresh, old), book.of(A));
    }

    @Test
    void onlyTheOwnerDeletesOrShares() {
        PhotoBook book = new PhotoBook();
        PhotoEntry e = photo(A, 1);
        book.add(e, 10);
        assertNull(book.delete(e.id(), B));
        assertFalse(book.share(e.id(), B));
        assertTrue(book.share(e.id(), A));
        assertTrue(book.get(e.id()).shared());
        assertNotNull(book.delete(e.id(), A));
        assertNull(book.get(e.id()));
    }

    @Test
    void roundTripsThroughNbt() {
        PhotoBook book = new PhotoBook();
        PhotoEntry e = photo(A, 42).withShared(true);
        book.add(e, 10);
        PhotoBook back = PhotoBook.load(book.save(new CompoundTag(), null));
        assertEquals(e, back.get(e.id()));
    }
}
