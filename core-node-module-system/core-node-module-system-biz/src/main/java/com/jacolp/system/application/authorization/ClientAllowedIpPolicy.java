package com.jacolp.system.application.authorization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict allow-list matching for a client's directly observed socket address.
 *
 * <p>This type intentionally does not inspect forwarded headers and does not use name resolution. Both the
 * configured entries and the observed remote address must be IP literals.</p>
 */
public final class ClientAllowedIpPolicy {

    private static final String INVALID_CONFIGURATION = "Invalid allowed_ips configuration";
    private static final String INVALID_REMOTE_ADDRESS = "Invalid socket remote address";

    private final List<Cidr> allowedCidrs;

    private ClientAllowedIpPolicy(List<Cidr> allowedCidrs) {
        this.allowedCidrs = List.copyOf(allowedCidrs);
    }

    public static ClientAllowedIpPolicy parse(String allowedIps) {
        if (allowedIps == null || allowedIps.trim().isEmpty()) {
            throw invalidConfiguration();
        }
        String[] entries = allowedIps.split(",", -1);
        List<Cidr> cidrs = new ArrayList<>(entries.length);
        Set<String> canonicalCidrs = new HashSet<>();
        for (String entry : entries) {
            String normalizedEntry = entry.trim();
            if (normalizedEntry.isEmpty()) {
                throw invalidConfiguration();
            }
            Cidr cidr;
            try {
                cidr = Cidr.parse(normalizedEntry);
            } catch (IllegalArgumentException exception) {
                throw invalidConfiguration();
            }
            if (!canonicalCidrs.add(cidr.canonicalKey())) {
                throw invalidConfiguration();
            }
            cidrs.add(cidr);
        }
        return new ClientAllowedIpPolicy(cidrs);
    }

    public boolean allows(String remoteAddress) {
        Address address;
        try {
            if (remoteAddress == null || remoteAddress.trim().isEmpty() || remoteAddress.indexOf('/') >= 0) {
                throw invalidRemoteAddress();
            }
            address = Address.parse(remoteAddress.trim());
        } catch (IllegalArgumentException exception) {
            throw invalidRemoteAddress();
        }
        return allowedCidrs.stream().anyMatch(cidr -> cidr.matches(address));
    }

    static byte[] canonicalSocketAddress(String remoteAddress) {
        try {
            if (remoteAddress == null || remoteAddress.trim().isEmpty() || remoteAddress.indexOf('/') >= 0) {
                throw invalidRemoteAddress();
            }
            Address address = Address.parse(remoteAddress.trim());
            byte[] identity = new byte[address.bytes.length + 1];
            identity[0] = (byte) address.family.byteLength;
            System.arraycopy(address.bytes, 0, identity, 1, address.bytes.length);
            return identity;
        } catch (IllegalArgumentException exception) {
            throw invalidRemoteAddress();
        }
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException(INVALID_CONFIGURATION);
    }

    private static IllegalArgumentException invalidRemoteAddress() {
        return new IllegalArgumentException(INVALID_REMOTE_ADDRESS);
    }

    private enum AddressFamily {
        IPV4(4), IPV6(16);

        private final int byteLength;

        AddressFamily(int byteLength) {
            this.byteLength = byteLength;
        }
    }

    private record Cidr(Address address, int prefixLength) {

        private static Cidr parse(String value) {
            int separator = value.indexOf('/');
            if (separator <= 0 || separator != value.lastIndexOf('/') || separator == value.length() - 1) {
                throw invalidConfiguration();
            }
            Address address = Address.parse(value.substring(0, separator));
            int prefixLength = parsePrefix(value.substring(separator + 1), address.family);
            return new Cidr(address, prefixLength);
        }

        private boolean matches(Address candidate) {
            if (address.family != candidate.family) {
                return false;
            }
            int wholeBytes = prefixLength / 8;
            for (int index = 0; index < wholeBytes; index++) {
                if (address.bytes[index] != candidate.bytes[index]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xff << (8 - remainingBits)) & 0xff;
            return (address.bytes[wholeBytes] & mask) == (candidate.bytes[wholeBytes] & mask);
        }

        private String canonicalKey() {
            byte[] canonicalAddress = address.bytes.clone();
            int wholeBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            if (remainingBits != 0) {
                canonicalAddress[wholeBytes] = (byte) (canonicalAddress[wholeBytes]
                        & ((0xff << (8 - remainingBits)) & 0xff));
                wholeBytes++;
            }
            for (int index = wholeBytes; index < canonicalAddress.length; index++) {
                canonicalAddress[index] = 0;
            }
            StringBuilder key = new StringBuilder(address.family.name()).append('/').append(prefixLength).append(':');
            for (byte value : canonicalAddress) {
                key.append(String.format("%02x", value & 0xff));
            }
            return key.toString();
        }

        private static int parsePrefix(String value, AddressFamily family) {
            if (value.isEmpty() || (value.length() > 1 && value.charAt(0) == '0')) {
                throw invalidConfiguration();
            }
            int prefix = 0;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character < '0' || character > '9') {
                    throw invalidConfiguration();
                }
                prefix = prefix * 10 + (character - '0');
                if (prefix > family.byteLength * 8) {
                    throw invalidConfiguration();
                }
            }
            return prefix;
        }
    }

    private record Address(AddressFamily family, byte[] bytes) {

        private static Address parse(String value) {
            if (value == null || value.isEmpty() || value.indexOf('[') >= 0 || value.indexOf(']') >= 0
                    || value.indexOf('%') >= 0) {
                throw invalidConfiguration();
            }
            return value.indexOf(':') >= 0 ? parseIpv6(value) : parseIpv4(value);
        }

        private static Address parseIpv4(String value) {
            String[] components = value.split("\\.", -1);
            if (components.length != 4) {
                throw invalidConfiguration();
            }
            byte[] bytes = new byte[AddressFamily.IPV4.byteLength];
            for (int index = 0; index < components.length; index++) {
                String component = components[index];
                if (component.isEmpty() || (component.length() > 1 && component.charAt(0) == '0')) {
                    throw invalidConfiguration();
                }
                int numericValue = 0;
                for (int characterIndex = 0; characterIndex < component.length(); characterIndex++) {
                    char character = component.charAt(characterIndex);
                    if (character < '0' || character > '9') {
                        throw invalidConfiguration();
                    }
                    numericValue = numericValue * 10 + (character - '0');
                    if (numericValue > 255) {
                        throw invalidConfiguration();
                    }
                }
                bytes[index] = (byte) numericValue;
            }
            return new Address(AddressFamily.IPV4, bytes);
        }

        private static Address parseIpv6(String value) {
            if (value.indexOf(':') < 0) {
                throw invalidConfiguration();
            }
            int compressedAt = value.indexOf("::");
            if (compressedAt != value.lastIndexOf("::")) {
                throw invalidConfiguration();
            }
            boolean compressed = compressedAt >= 0;
            String leftValue = compressed ? value.substring(0, compressedAt) : value;
            String rightValue = compressed ? value.substring(compressedAt + 2) : "";
            List<Integer> left = parseIpv6Side(leftValue, !compressed);
            List<Integer> right = compressed ? parseIpv6Side(rightValue, true) : List.of();
            int groupCount = left.size() + right.size();
            if ((compressed && groupCount >= 8) || (!compressed && groupCount != 8)) {
                throw invalidConfiguration();
            }

            List<Integer> groups = new ArrayList<>(8);
            groups.addAll(left);
            if (compressed) {
                for (int index = groupCount; index < 8; index++) {
                    groups.add(0);
                }
            }
            groups.addAll(right);
            byte[] bytes = new byte[AddressFamily.IPV6.byteLength];
            for (int index = 0; index < groups.size(); index++) {
                int group = groups.get(index);
                bytes[index * 2] = (byte) (group >>> 8);
                bytes[index * 2 + 1] = (byte) group;
            }
            return new Address(AddressFamily.IPV6, bytes);
        }

        private static List<Integer> parseIpv6Side(String value, boolean allowIpv4Tail) {
            if (value.isEmpty()) {
                return List.of();
            }
            String[] components = value.split(":", -1);
            List<Integer> groups = new ArrayList<>(components.length + 1);
            for (int index = 0; index < components.length; index++) {
                String component = components[index];
                if (component.isEmpty()) {
                    throw invalidConfiguration();
                }
                if (component.indexOf('.') >= 0) {
                    if (!allowIpv4Tail || index != components.length - 1) {
                        throw invalidConfiguration();
                    }
                    byte[] ipv4Bytes = parseIpv4(component).bytes;
                    groups.add(((ipv4Bytes[0] & 0xff) << 8) | (ipv4Bytes[1] & 0xff));
                    groups.add(((ipv4Bytes[2] & 0xff) << 8) | (ipv4Bytes[3] & 0xff));
                    continue;
                }
                if (component.length() > 4) {
                    throw invalidConfiguration();
                }
                int group = 0;
                for (int characterIndex = 0; characterIndex < component.length(); characterIndex++) {
                    int hex = asciiHexValue(component.charAt(characterIndex));
                    if (hex < 0) {
                        throw invalidConfiguration();
                    }
                    group = (group << 4) | hex;
                }
                groups.add(group);
            }
            return groups;
        }

        private static int asciiHexValue(char character) {
            if (character >= '0' && character <= '9') {
                return character - '0';
            }
            if (character >= 'a' && character <= 'f') {
                return character - 'a' + 10;
            }
            if (character >= 'A' && character <= 'F') {
                return character - 'A' + 10;
            }
            return -1;
        }
    }
}
