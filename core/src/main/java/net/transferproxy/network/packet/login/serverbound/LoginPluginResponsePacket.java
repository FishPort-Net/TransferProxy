/*
 * MIT License
 *
 * Copyright (c) 2026 Lily Ellenvia
 * Modifications and adaptations for TransferProxy
 *
 * Based on NeoForged-Velocity-Support by Swedz (https://swedz.net/)
 * https://github.com/Swedz/NeoForged-Velocity-Support
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.transferproxy.network.packet.login.serverbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.transferproxy.api.TransferProxy;
import net.transferproxy.api.event.EventType;
import net.transferproxy.api.event.login.PreLoginEvent;
import net.transferproxy.api.network.connection.PlayerConnection;
import net.transferproxy.api.network.packet.serverbound.ServerboundPacket;
import net.transferproxy.api.network.protocol.Protocolized;
import net.transferproxy.forwarding.VelocityForwardingState;
import net.transferproxy.forwarding.VelocityForwardingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.UUID;

import static net.transferproxy.util.BufUtil.*;

public record LoginPluginResponsePacket(
    int transactionId,
    byte @Nullable [] payload
) implements ServerboundPacket {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginPluginResponsePacket.class);

    public LoginPluginResponsePacket(final @NotNull ByteBuf buf) {
        this(readVarInt(buf), readOptionalPayload(buf));
    }

    private static byte @Nullable [] readOptionalPayload(final @NotNull ByteBuf buf) {
        if (buf.readBoolean()) {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        }
        return null;
    }

    @Override
    public void handle(final @NotNull PlayerConnection connection) {
        // 获取 Channel 上存储的 forwarding 状态（同时清除，防止重复处理）
        final VelocityForwardingState state = connection.getChannel()
            .attr(VelocityForwardingState.KEY).getAndSet(null);

        if (state == null) {
            // 没有 forwarding 状态：说明未启用 forwarding 或收到意外的 0x02 包
            // 直接忽略（PacketDecoder 保证此包只在 LOGIN 状态到达）
            return;
        }

        // 验证 Transaction ID
        if (state.getTransactionId() != this.transactionId) {
            LOGGER.warn("Mismatched transaction ID: expected {}, got {}",
                state.getTransactionId(), this.transactionId);
            connection.disconnect("Forwarding verification failed");
            return;
        }

        // 无 payload 表示对端不支持（原版客户端）
        if (this.payload == null) {
            final var config = TransferProxy.getInstance().getConfiguration().getForwarding();
            if (config.isRequired()) {
                connection.disconnect("You must connect through the proxy.");
                return;
            }
            // 非强制模式：使用 LoginStart 中的原始数据继续
            resumeLogin(connection, state.getLoginUuid(), state.getLoginName());
            return;
        }

        // 解析 forwarding 数据
        final ByteBuf buf = Unpooled.wrappedBuffer(this.payload);
        try {
            final var config = TransferProxy.getInstance().getConfiguration().getForwarding();
            final var secretSpec = config.getSecretSpec();
            if (secretSpec == null) {
                LOGGER.error("Forwarding secret not loaded, cannot verify");
                connection.disconnect("Forwarding verification failed");
                return;
            }

            // 验证 HMAC 签名
            if (!VelocityForwardingUtil.checkIntegrity(buf, secretSpec)) {
                connection.disconnect("Unable to verify player details.");
                return;
            }

            // 读取版本号
            int version = readVarInt(buf);
            if (version > VelocityForwardingUtil.MAX_SUPPORTED_FORWARDING_VERSION) {
                LOGGER.error("Unsupported forwarding version {}, max supported: {}",
                    version, VelocityForwardingUtil.MAX_SUPPORTED_FORWARDING_VERSION);
                connection.disconnect("Forwarding verification failed");
                return;
            }

            // 读取玩家数据
            InetAddress address = VelocityForwardingUtil.readAddress(buf);
            VelocityForwardingUtil.ForwardedProfile profile = VelocityForwardingUtil.readProfile(buf);
            UUID uuid = profile.uuid();
            String username = profile.username();
            // Properties 跳过（TransferProxy 不需要纹理等数据）
            VelocityForwardingUtil.skipProperties(buf);

            LOGGER.info("Velocity forwarding: player {} (UUID: {}, IP: {})",
                username, uuid, address);

            // 使用验证后的数据继续登录（setProfile 将在 resumeLogin 中使用事件的值后调用）
            resumeLogin(connection, uuid, username);

        } catch (Exception e) {
            LOGGER.error("Failed to process forwarding data", e);
            connection.disconnect("Forwarding verification failed");
        } finally {
            buf.release();
        }
    }

    private static void resumeLogin(final @NotNull PlayerConnection connection,
                                    final @NotNull UUID uuid,
                                    final @NotNull String username) {
        final PreLoginEvent event = new PreLoginEvent(connection, uuid, username);
        TransferProxy.getInstance().getModuleManager().getEventManager()
            .call(EventType.PRE_LOGIN, event);
        if (event.canSendSuccessPacket()) {
            // Set profile using event's potentially modified values
            connection.setProfile(event.getUsername(), event.getUUID());
            connection.sendLoginSuccess(event.getUUID(), event.getUsername());
        }
    }

    @Override
    public void write(final @NotNull Protocolized protocolized, final @NotNull ByteBuf buf) {
        writeVarInt(buf, this.transactionId);
        buf.writeBoolean(this.payload != null);
        if (this.payload != null) {
            buf.writeBytes(this.payload);
        }
    }

    @Override
    public int getId() {
        return 0x02;
    }
}
