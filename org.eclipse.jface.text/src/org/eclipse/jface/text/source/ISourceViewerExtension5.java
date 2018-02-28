/**
 *  Copyright (c) 2017 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - [CodeMining] Add CodeMining support in SourceViewer - Bug 527515
 */
package org.eclipse.jface.text.source;

import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;

/**
 * Extension interface for {@link org.eclipse.jface.text.source.ISourceViewer}.
 * <p>
 * It introduces API to access a minimal set of code mining APIs.</li>
 * </p>
 *
 * @see ICodeMining
 * @see ICodeMiningProvider
 * @since 3.13
 */
public interface ISourceViewerExtension5 {

	/**
	 * Set the {@link AnnotationPainter} to use to draw code minings.
	 *
	 * @param painter the {@link AnnotationPainter} to use to draw code minings.
	 */
	void setCodeMiningAnnotationPainter(AnnotationPainter painter);

	/**
	 * Register the code mining providers.
	 *
	 * @param codeMiningProviders the code mining providers to register.
	 */
	void setCodeMiningProviders(ICodeMiningProvider[] codeMiningProviders);

	/**
	 * Returns true there are code mining providers and false otherwise.
	 *
	 * @return true there are code mining providers and false otherwise.
	 */
	boolean hasCodeMiningProviders();

	/**
	 * Update the code minings.
	 *
	 * Clients and implementors are responsible of calling this method when needed. A typical
	 * use-case can be to run it upon completion of a reconcilier and after a job that would compute
	 * all the necessary pre-requisites to insert code mining annotations.
	 */
	void updateCodeMinings();

}