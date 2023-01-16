package org.hibernate.infra.bot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;

public class StringListArgumentConverter extends SimpleArgumentConverter {
	private static final String SEPARATOR = ";";

	@Override
	protected List<String> convert(Object source, Class<?> targetType) throws ArgumentConversionException {
		if ( source == null ) {
			return Collections.emptyList();
		}
		if ( !( source instanceof String sourceString ) ) {
			throw new ArgumentConversionException( "Can only convert from String" );
		}
		if ( targetType != List.class ) {
			throw new ArgumentConversionException( "Can only convert to List<String>" );
		}
		if ( sourceString.isEmpty() ) {
			return Collections.emptyList();
		}
		return Arrays.asList( sourceString.split( SEPARATOR ) );
	}
}
