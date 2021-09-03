/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2021 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.retrooper.packetevents.wrapper.game.server;

import io.github.retrooper.packetevents.event.impl.PacketSendEvent;
import io.github.retrooper.packetevents.manager.player.ClientVersion;
import io.github.retrooper.packetevents.protocol.PacketType;
import io.github.retrooper.packetevents.utils.StringUtil;
import io.github.retrooper.packetevents.wrapper.PacketWrapper;

public class WrapperGameServerDisconnect extends PacketWrapper<WrapperGameServerDisconnect> {
    private static final int MODERN_MESSAGE_LENGTH = 262144;
    private static final int LEGACY_MESSAGE_LENGTH = 32767;
    private String reason;
    public WrapperGameServerDisconnect(PacketSendEvent event) {
        super(event);
    }

    public WrapperGameServerDisconnect(String reason) {
        super(PacketType.Game.Server.DISCONNECT.getID());
        this.reason = reason;
    }

    @Override
    public void readData() {
        int maxMessageLength = clientVersion.isNewerThanOrEquals(ClientVersion.v_1_13) ? MODERN_MESSAGE_LENGTH : LEGACY_MESSAGE_LENGTH;
        this.reason = readString(maxMessageLength);
    }

    @Override
    public void readData(WrapperGameServerDisconnect wrapper) {
        this.reason = wrapper.reason;
    }

    @Override
    public void writeData() {
        int maxMessageLength = clientVersion.isNewerThanOrEquals(ClientVersion.v_1_13) ? MODERN_MESSAGE_LENGTH : LEGACY_MESSAGE_LENGTH;
        reason = StringUtil.maximizeLength(reason, maxMessageLength);
        writeString(reason, maxMessageLength);
    }
}
