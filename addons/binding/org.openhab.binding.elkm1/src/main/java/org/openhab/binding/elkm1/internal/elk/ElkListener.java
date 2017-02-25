/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.elkm1.internal.elk;

/**
 * Handles the elk messages generate from the elk system.
 *
 * @author David Bennett - Initial Contribution
 */
public interface ElkListener {
    void handleElkMessage(ElkMessage message);
}
