/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.text.revisions;

import org.eclipse.core.runtime.Assert;

/**
 * Informs about a change of revision information. Clients may use but not instantiate this class.
 * <p>
 * <em>This API is provisional and may change any time before the 3.3 API freeze.</em>
 * </p>
 * 
 * @since 3.3
 */
public final class RevisionEvent {
	private final RevisionInformation fInformation;

	/**
	 * Creates a new event.
	 * 
	 * @param information the revision info
	 */
	public RevisionEvent(RevisionInformation information) {
		Assert.isLegal(information != null);
		fInformation= information;
	}

	/**
	 * Returns the revision information that has changed.
	 * 
	 * @return the revision information that has changed
	 */
	public RevisionInformation getRevisionInformation() {
		return fInformation;
	}
}
