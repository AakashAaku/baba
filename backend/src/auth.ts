import crypto from "node:crypto";
import type { IncomingHttpHeaders } from "node:http";
import type { AuthUser } from "./models.ts";

export class HttpError extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function createSignedDevToken(user: AuthUser, secret: string): string {
  const payload = Buffer.from(JSON.stringify(user), "utf8").toString("base64url");
  const sig = crypto.createHmac("sha256", secret).update(payload).digest("base64url");
  return `baba-dev.${payload}.${sig}`;
}

export function verifyAuthHeader(
  headers: IncomingHttpHeaders,
  authSecret: string,
  allowUnsignedJwtInDev = false
): AuthUser {
  const rawAuth = headers.authorization;
  if (!rawAuth || typeof rawAuth !== "string" || !rawAuth.startsWith("Bearer ")) {
    throw new HttpError(401, "UNAUTHENTICATED", "Missing or malformed Authorization Bearer token.");
  }

  const token = rawAuth.slice("Bearer ".length).trim();
  if (!token) {
    throw new HttpError(401, "UNAUTHENTICATED", "Empty Bearer token.");
  }

  // Verified HMAC dev/test token format: baba-dev.<base64urlPayload>.<base64urlSig>
  if (token.startsWith("baba-dev.")) {
    const parts = token.split(".");
    if (parts.length !== 3) {
      throw new HttpError(401, "INVALID_TOKEN", "Malformed signed token.");
    }
    const payload = parts[1];
    const sig = parts[2];
    const expectedSig = crypto.createHmac("sha256", authSecret).update(payload).digest("base64url");
    const sigBuf = Buffer.from(sig, "utf8");
    const expBuf = Buffer.from(expectedSig, "utf8");
    if (sigBuf.length !== expBuf.length || !crypto.timingSafeEqual(sigBuf, expBuf)) {
      throw new HttpError(401, "INVALID_SIGNATURE", "Token signature verification failed.");
    }
    const parsed = JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as AuthUser;
    if (!parsed.uid || typeof parsed.uid !== "string") {
      throw new HttpError(401, "INVALID_CLAIMS", "Token missing uid claim.");
    }
    return {
      uid: parsed.uid,
      email: (parsed.email || "").toLowerCase(),
      emailVerified: Boolean(parsed.emailVerified),
      name: parsed.name || "Member",
    };
  }

  // Standard 3-part Firebase/OIDC JWT decode when explicitly configured behind API Gateway / Identity-Aware Proxy
  const jwtParts = token.split(".");
  if (allowUnsignedJwtInDev && jwtParts.length === 3) {
    try {
      const claims = JSON.parse(Buffer.from(jwtParts[1], "base64url").toString("utf8")) as Record<string, unknown>;
      const uid = typeof claims.user_id === "string" ? claims.user_id : typeof claims.sub === "string" ? claims.sub : "";
      if (!uid) {
        throw new Error("missing sub");
      }
      return {
        uid,
        email: typeof claims.email === "string" ? claims.email.toLowerCase() : "",
        emailVerified: Boolean(claims.email_verified),
        name: typeof claims.name === "string" ? claims.name : "Member",
      };
    } catch {
      throw new HttpError(401, "INVALID_JWT", "Failed to decode JWT payload.");
    }
  }

  throw new HttpError(401, "INVALID_TOKEN", "Unsupported or unverified Bearer token.");
}
