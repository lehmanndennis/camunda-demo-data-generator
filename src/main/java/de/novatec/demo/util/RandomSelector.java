package de.novatec.demo.util;

import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import de.novatec.demo.model.IProbability;
import lombok.extern.java.Log;

/**
 * This class is used to get random access with a predefined Probability to an
 * item in a List. Therefore all elements in the List must extend
 * {@link IProbability} to verify that the probability is given. All items in
 * the List must have a summed probability of 100.
 * 
 * @param <T>
 */
@Log
public class RandomSelector<T extends IProbability> {

	private static final Random RANDOM = new Random();
	private List<T> items;

	/**
	 * Initializes the random selector or throws an Exception if the summed
	 * probability is not 100.
	 * 
	 * @param items
	 */
	public RandomSelector(List<T> items) {
		super();
		this.items = items;
		int totalSum = getTotalSum();
		if (totalSum == 0) {
			log.log(Level.FINE, "Using defaults");
		} else if (totalSum != 100) {
			throw new IllegalArgumentException("Probability not 100%");
		}
	}

	/**
	 * Used to get a randomly selected element from the List.
	 * 
	 * @return returns the selected element chosen by probability.
	 */
	public T getRandom() {
		if (getTotalSum() == 0) {
			return null;
		}
		int index = RANDOM.nextInt(getTotalSum());
		int sum = 0;
		int i = 0;
		while (sum < index) {
			T item = items.get(i++);
			sum += item != null ? item.getProbability() : 0;
		}
		return items.get(Math.max(0, i - 1));
	}

	private int getTotalSum() {
		int totalSum = 0;
		for (T item : items) {
			if (item == null) {
				continue;
			}
			totalSum = totalSum + item.getProbability();
		}
		return totalSum;
	}
}