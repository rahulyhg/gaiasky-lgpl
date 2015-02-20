package gaia.cu9.ari.gaiaorbit.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * A structure composed of a set of collections of a given size in which
 * additions happen to the next list in the roulette before it is spun. 
 * @author Toni Sagrista
 *
 */
public class RouletteList<T> implements Collection<T> {

    private Collection<T>[] roulette;
    private int currentIndex;
    private int numElements;

    /**
     * Constructs a roulette list with a given size.
     * @param size The number of collections.
     * @param initialCollectionSize The initial size of each collection.
     */
    @SuppressWarnings("unchecked")
    public RouletteList(int size, int initialCollectionSize) {
	roulette = new Collection[size];
	for (int i = 0; i < size; i++) {
	    roulette[i] = new ArrayList<T>(initialCollectionSize);
	}
	currentIndex = 0;
    }

    /**
     * Returns the collection for the given index. If the index is negative
     * or greater than the number of collections in the roulette, it asserts an 
     * exception.
     * @param index The index.
     * @return The collection at the given index in this roulette.
     */
    public Collection<T> getCollection(int index) {
	assert index >= 0 && index < roulette.length : "Index out of bounds: " + index;
	return roulette[index];
    }

    public int getNumCollections() {
	return roulette.length;
    }

    @Override
    public int size() {
	return numElements;
    }

    @Override
    public boolean isEmpty() {
	for (int i = 0; i < roulette.length; i++) {
	    if (!roulette[i].isEmpty())
		return false;
	}
	return true;
    }

    @Override
    public boolean contains(Object o) {
	for (int i = 0; i < roulette.length; i++) {
	    if (roulette[i].contains(o))
		return true;
	}
	return false;
    }

    @Override
    /** Not supported, returns null **/
    public Iterator<T> iterator() {
	return null;
    }

    @Override
    public T[] toArray() {
	T[] result = (T[]) new Object[numElements];
	return toArrayConcrete(result);
    }

    @Override
    public <T> T[] toArray(T[] a) {
	if (a.length >= numElements) {
	    return toArrayConcrete(a);
	} else {
	    T[] result = (T[]) new Object[numElements];
	    return toArrayConcrete(result);
	}
    }

    public <T> T[] toArrayConcrete(T[] a) {
	int idx = 0;
	for (int i = 0; i < roulette.length; i++) {
	    Iterator<T> it = (Iterator<T>) roulette[i].iterator();
	    while (it.hasNext()) {
		a[idx] = it.next();
		idx++;
	    }
	}
	return a;
    }

    @Override
    public boolean add(T e) {
	boolean result = roulette[currentIndex].add(e);
	numElements++;
	currentIndex = (currentIndex + 1) % roulette.length;
	return result;
    }

    @Override
    public boolean remove(Object o) {
	for (int i = 0; i < roulette.length; i++) {
	    if (roulette[i].remove(o)) {
		numElements--;
		return true;
	    }
	}
	return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
	return Arrays.asList(toArray()).containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
	Iterator<? extends T> it = c.iterator();
	boolean result = false;
	while (it.hasNext()) {
	    result = result || add(it.next());
	}
	return result;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
	Iterator<?> it = c.iterator();
	boolean result = false;
	while (it.hasNext()) {
	    result = result || remove(it.next());
	}
	return result;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
	boolean result = false;
	numElements = 0;
	for (int i = 0; i < roulette.length; i++) {
	    result = result || roulette[i].retainAll(c);
	    numElements += roulette[i].size();
	}
	return result;
    }

    @Override
    public void clear() {
	for (int i = 0; i < roulette.length; i++) {
	    roulette[i].clear();
	}
	numElements = 0;

    }

}