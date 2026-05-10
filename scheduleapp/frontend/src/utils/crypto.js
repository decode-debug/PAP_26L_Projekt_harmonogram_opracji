/**
 * Encrypts plaintext with an RSA public key (OAEP-SHA256) using the Web Crypto API.
 * Returns a Base64-encoded ciphertext string.
 */
export async function rsaEncrypt(plaintext, publicKeyBase64) {
  const binaryDer = Uint8Array.from(atob(publicKeyBase64), c => c.charCodeAt(0));
  const publicKey = await window.crypto.subtle.importKey(
    'spki',
    binaryDer.buffer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt']
  );
  const encoded = new TextEncoder().encode(plaintext);
  const encrypted = await window.crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, encoded);
  return btoa(String.fromCharCode(...new Uint8Array(encrypted)));
}
