package de.novatec.demo.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import de.novatec.demo.model.IProbability;

public class RandomSelectorTest {

	private List<IProbability> items = new ArrayList<>();
	private RandomSelector<IProbability> randomSelector = new RandomSelector<>(items);

	@Before
	public void cleanUp() {
		items.clear();
	}

	@Test
	public void testEmptyList() {
		IProbability result = randomSelector.getRandom();

		assertNull(result);
	}

	@Test
	public void testSingleItem() {
		IProbability item100 = new TestItem();
		item100.setProbability(100);
		items.add(item100);

		IProbability result = randomSelector.getRandom();

		assertEquals(item100, result);
	}

	@Test
	public void testSecondItem() {
		IProbability item100 = new TestItem();
		item100.setProbability(100);
		IProbability item0 = new TestItem();
		item0.setProbability(0);
		items.add(item0);
		items.add(item100);

		IProbability result = randomSelector.getRandom();

		assertEquals(item100, result);
	}

	@Test
	public void testFirstItem() {
		IProbability item100 = new TestItem();
		item100.setProbability(100);
		IProbability item0 = new TestItem();
		item0.setProbability(0);
		items.add(item100);
		items.add(item0);

		IProbability result = randomSelector.getRandom();

		assertEquals(item100, result);
	}

	@Test
	public void testNull() {
		items.add(null);

		IProbability result = randomSelector.getRandom();

		assertNull(result);
	}

	@Test
	public void testNullBetween() {
		IProbability item100 = new TestItem();
		item100.setProbability(100);
		IProbability item0 = new TestItem();
		item0.setProbability(0);
		items.add(item0);
		items.add(null);
		items.add(item100);

		IProbability result = randomSelector.getRandom();

		assertEquals(item100, result);
	}

	@Test
	public void testDefaults() {
		IProbability item = new TestItem();
		items.add(item);
		item = new TestItem();
		items.add(item);
		item = new TestItem();
		items.add(item);

		IProbability result = randomSelector.getRandom();

		assertNotNull(result);
	}

	private static class TestItem implements IProbability {
		private int probability;

		@Override
		public int getProbability() {
			return probability;
		}

		@Override
		public void setProbability(int probability) {
			this.probability = probability;
		}
	}
}
