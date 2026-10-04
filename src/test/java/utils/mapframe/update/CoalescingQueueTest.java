package utils.mapframe.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoalescingQueueTest {
    @Test
    void preservesFifoOrderAndCoalescesDuplicates() {
        CoalescingQueue<String> queue = new CoalescingQueue<>(2);

        assertTrue(queue.offer("first"));
        assertTrue(queue.offer("first"));
        assertTrue(queue.offer("second"));
        assertFalse(queue.offer("third"));
        assertEquals(2, queue.size());
        assertEquals("first", queue.poll());
        assertEquals("second", queue.poll());
        assertNull(queue.poll());
    }
}
