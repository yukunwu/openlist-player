// Deliberately exclude RSA, ASN.1, certificates and Forge's random generator.
require('node-forge/lib/aes');
const forge = require('node-forge/lib/forge');
window.forge = {cipher: forge.cipher, util: forge.util};
