package ph.edu.dlsu.anilink.dsa;

import java.util.Objects;

public class AniLinkHashMap<K, V> {

    private static class Node<K, V> {
        final K key;
        V value;
        Node<K, V> next;

        Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private Node<K, V>[] buckets;
    private int size;

    @SuppressWarnings("unchecked")
    public AniLinkHashMap() {
        this.buckets = (Node<K, V>[]) new Node[DEFAULT_CAPACITY];
        this.size = 0;
    }

    private int getBucketIndex(K key, int capacity) {
        if (key == null) return 0;
        // Spread higher bits to reduce collisions, then mask with (capacity - 1)
        int h = key.hashCode();
        int spreadHash = h ^ (h >>> 16);
        return spreadHash & (capacity - 1);
    }

    public void put(K key, V value) {
        if ((float) (size + 1) / buckets.length > LOAD_FACTOR) {
            resize();
        }

        int index = getBucketIndex(key, buckets.length);
        Node<K, V> current = buckets[index];

        // Update existing key if found
        while (current != null) {
            if (Objects.equals(current.key, key)) {
                current.value = value;
                return;
            }
            current = current.next;
        }

        // Insert new node at the head of the bucket
        buckets[index] = new Node<>(key, value, buckets[index]);
        size++;
    }

    public V get(K key) {
        int index = getBucketIndex(key, buckets.length);
        Node<K, V> current = buckets[index];

        while (current != null) {
            if (Objects.equals(current.key, key)) {
                return current.value;
            }
            current = current.next;
        }
        return null;
    }

    public V remove(K key) {
        int index = getBucketIndex(key, buckets.length);
        Node<K, V> current = buckets[index];
        Node<K, V> prev = null;

        while (current != null) {
            if (Objects.equals(current.key, key)) {
                if (prev == null) {
                    buckets[index] = current.next;
                } else {
                    prev.next = current.next;
                }
                size--;
                return current.value;
            }
            prev = current;
            current = current.next;
        }
        return null;
    }

    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Node<K, V>[] oldBuckets = buckets;
        int newCapacity = oldBuckets.length * 2;
        buckets = (Node<K, V>[]) new Node[newCapacity];

        for (Node<K, V> head : oldBuckets) {
            while (head != null) {
                Node<K, V> nextNode = head.next;
                int newIndex = getBucketIndex(head.key, newCapacity);

                // Re-insert node at the head of the new bucket
                head.next = buckets[newIndex];
                buckets[newIndex] = head;

                head = nextNode;
            }
        }
    }
}
