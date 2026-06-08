/*
 *     MCEF (Minecraft Chromium Embedded Framework)
 *     Copyright (C) 2023 CinemaMod Group
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */

/*
 * little unsure of if I should uncomment this or not
 * basically; forge's way of doing this sorta "listening for a thing" is events
 * this allows that to be used for MCEF initalization
 *
 * there is a fabric-equivalent-expectation for this, but it's sorta just what's already in the MCEF class if I'm remembering correctly
 */

//package com.cinemamod.mcef;
//
//import net.minecraftforge.common.MinecraftForge;
//import net.minecraftforge.eventbus.api.Event;
//
//public class MCEFInitEvent extends Event {
//	static {
//		MCEF.scheduleForInit(() -> {
//			MinecraftForge.EVENT_BUS.post(new MCEFInitEvent());
//		});
//	}
//
//	public MCEFInitEvent() {
//	}
//}
