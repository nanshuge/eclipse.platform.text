package org.eclipse.jface.text.rules;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

/**
 * A rule for detecting patterns which begin with a given 
 * sequence and may end with a given sequence thereby spanning
 * multiple lines.
 */
public class MultiLineRule extends PatternRule {

	/**
	 * Creates a rule for the given starting and ending sequence
	 * which, if detected, will return the specified token.
	 *
	 * @param startSequence the pattern's start sequence
	 * @param endSequence the pattern's end sequence
	 * @param token the token to be returned on success
	 */
	public MultiLineRule(String startSequence, String endSequence, IToken token) {
		this(startSequence, endSequence, token, (char)0);
	}

	/**
	 * Creates a rule for the given starting and ending sequence
	 * which, if detected, will return the specific token.
	 * Any character which follows the given escape character will be ignored.
	 *
	 * @param startSequence the pattern's start sequence
	 * @param endSequence the pattern's end sequence
	 * @param token the token to be returned on success
	 * @param escapeCharacter the escape character
	 */
	public MultiLineRule(String startSequence, String endSequence, IToken token, char escapeCharacter) {
		super(startSequence, endSequence, token, escapeCharacter, false);
	}
}