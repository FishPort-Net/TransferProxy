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

package net.transferproxy.forwarding;

import io.netty.util.AttributeKey;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class VelocityForwardingState {

    public static final AttributeKey<VelocityForwardingState> KEY =
        AttributeKey.valueOf("velocity_forwarding_state");

    private final int transactionId;
    private final String loginName;
    private final UUID loginUuid;

    public VelocityForwardingState(int transactionId,
                                   @NotNull String loginName,
                                   @NotNull UUID loginUuid) {
        this.transactionId = transactionId;
        this.loginName = loginName;
        this.loginUuid = loginUuid;
    }

    public int getTransactionId() {
        return this.transactionId;
    }

    public String getLoginName() {
        return this.loginName;
    }

    public UUID getLoginUuid() {
        return this.loginUuid;
    }
}
