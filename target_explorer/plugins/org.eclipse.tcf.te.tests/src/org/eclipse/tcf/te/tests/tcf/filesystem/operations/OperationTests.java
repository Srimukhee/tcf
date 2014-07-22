/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tests.tcf.filesystem.operations;

import junit.framework.Test;
import junit.framework.TestSuite;

public class OperationTests {
	public static Test suite() {
		TestSuite suite = new TestSuite("File System: Operation Tests"); //$NON-NLS-1$
		suite.addTestSuite(FSCopyTests.class);
		suite.addTestSuite(FSCreateFileTests.class);
		suite.addTestSuite(FSCreateFolderTests.class);
		suite.addTestSuite(FSDeleteTests.class);
		suite.addTestSuite(FSMoveTests.class);
		suite.addTestSuite(FSRefreshTests.class);
		suite.addTestSuite(FSRenameTests.class);
		suite.addTestSuite(FSUploadTest.class);
		suite.addTestSuite(FSCacheCommitTest.class);
		suite.addTestSuite(FSCacheUpdateTest.class);
		return suite;
	}
}
