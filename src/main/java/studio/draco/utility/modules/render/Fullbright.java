/*
 * Draco Utility - client framework for Minecraft (Fabric)
 * Copyright (C) 2026 DUCNGUYEN-creator (Draco Studio)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package studio.draco.utility.modules.render;

import studio.draco.utility.core.module.Module;
import studio.draco.utility.core.policy.Capability;
import studio.draco.utility.core.module.ModuleCategory;
import studio.draco.utility.services.NightVision;

public final class Fullbright extends Module {
    public Fullbright() {
        super("fullbright", "Fullbright", "Client-side night vision. The server does not know about it.", ModuleCategory.RENDER);
        capabilities(Capability.LOCAL_VISUAL);
    }

    @Override
    protected void onActivate() {
        NightVision.acquire(this);
    }

    @Override
    protected void onDeactivate() {
        NightVision.release(this);
    }
}
