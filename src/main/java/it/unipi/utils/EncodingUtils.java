package it.unipi.utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.log;

public class EncodingUtils {

    private static byte[] encodeNumber(int n) {
        if (n == 0) {
            return new byte[]{(byte) 128};
        }
        int i = (int) (log(n) / log(128)) + 1;
        byte[] bytes = new byte[i];
        int j = i - 1;
        do {
            bytes[j--] = (byte) (n % 128);
            n /= 128;
        } while (j >= 0);
        bytes[i - 1] += 128;
        return bytes;
    }

    public static byte[] encode(List<Integer> numbers) {
        byte[] byteStream;
        try {
            byteStream = new byte[getEncodingLength(numbers)];
        } catch (NegativeArraySizeException e) {
            byteStream = new byte[50];
            e.printStackTrace();
        }
        byte[] bytes;
        int i = 0;
        for (Integer number: numbers) {
            bytes = encodeNumber(number);
            for (byte byteElem: bytes)
                byteStream[i++] = byteElem;
        }
        return byteStream;
    }

    public static int getEncodingLength(List<Integer> numbers){
        int bytesLength = 0;
        for (Integer number: numbers) {
            if (number == 0) {
                bytesLength += 1;
            } else {
                bytesLength += (int) (log(number) / log(128)) + 1;
            }
        }
        return bytesLength;
    }

    // TODO merge the following two functions
    public static ArrayList<Integer> decode(byte[] byteStream) {
        ArrayList<Integer> numbers = new ArrayList<>();
        int n = 0;
        for (byte byteElem : byteStream) {
            int unsignedByte = byteElem & 0xff;
            if (unsignedByte < 128) {
                n = 128 * n + unsignedByte;
            } else {
                n = 128 * n + (unsignedByte - 128);
                numbers.add(n);
                n = 0;
            }
        }
        return numbers;
    }

    public static ArrayList<Integer> decode(List<Byte> byteStream) {
        ArrayList<Integer> numbers = new ArrayList<>();
        int n = 0;
        for (byte byteElem : byteStream) {
            int unsignedByte = byteElem & 0xff;
            if (unsignedByte < 128) {
                n = 128 * n + unsignedByte;
            } else {
                n = 128 * n + (unsignedByte - 128);
                numbers.add(n);
                n = 0;
            }
        }
        return numbers;
    }

    //given an integer return the byte representation
    public static byte[] intToByteArray(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    public static byte[] doubleToByteArray(double value) {
        return ByteBuffer.allocate(8).putDouble(value).array();
    }

    public static byte[] longToByteArray(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    public static byte[] intListToByteArray(List<Integer> values) {
        byte[] bytes = new byte[values.size() * 4];
        int i = 0;
        for (Integer value: values) {
            byte[] tempBytes = intToByteArray(value);
            System.arraycopy(tempBytes, 0, bytes, (i * 4), 4);
            i++;
        }
        return bytes;
    }

    public static int byteArrayToInt(byte[] value, int startIndex) {
        return ByteBuffer.wrap(value).getInt(startIndex);
    }

    public static long byteArrayToLong(byte[] value, int startIndex) {
        return ByteBuffer.wrap(value).getLong(startIndex);
    }

    public static double byteArrayToDouble(byte[] value, int startIndex) {
        return ByteBuffer.wrap(value).getDouble(startIndex);
    }
}