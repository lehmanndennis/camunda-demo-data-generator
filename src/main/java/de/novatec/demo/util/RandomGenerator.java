package de.novatec.demo.util;

import java.util.Random;

import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.BooleanValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.DoubleValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.IntegerValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.StringValueImpl;
import org.camunda.bpm.engine.variable.value.TypedValue;

import de.novatec.demo.model.ProcessVariable;

public class RandomGenerator {
	private static Random RANDOM = new Random();

	public static TypedValue generate(ProcessVariable processVariable) {
		TypedValue value;
		switch (processVariable.getType()) {
		case BOOLEAN:
			value = new BooleanValueImpl(RANDOM.nextBoolean());
			break;
		case DOUBLE:
			value = new DoubleValueImpl(RANDOM.nextDouble() * 1000d);
			break;
		case INTEGER:
			value = new IntegerValueImpl(RANDOM.nextInt(1001));
			break;
		case STRING:
			value = new StringValueImpl(RandomStringUtils.randomAlphabetic(RANDOM.nextInt(50) + 1));
			break;
		default:
			value = null;
			break;
		}
		return value;
	}
}
