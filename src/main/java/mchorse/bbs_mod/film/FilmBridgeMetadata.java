package mchorse.bbs_mod.film;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Native film storage for bridge JSON larger than DataOutput.writeUTF permits. */
public final class FilmBridgeMetadata
{
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    private FilmBridgeMetadata()
    {}

    public static void write(MapType data, String key, String text)
    {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
        {
            throw new IllegalArgumentException("Bridge metadata capacity exceeded: " + key);
        }
        data.putByteArray(key, bytes);
    }

    public static String read(MapType data, String key)
    {
        BaseType value = data.get(key);
        if (value == null)
        {
            return "";
        }
        if (value.isString())
        {
            return value.asString();
        }
        if (!BaseType.is(value, BaseType.TYPE_BYTE_ARRAY))
        {
            throw new IllegalArgumentException("Invalid bridge metadata type: " + key);
        }
        byte[] bytes = data.getByteArray(key);
        if (bytes.length > MAX_BYTES)
        {
            throw new IllegalArgumentException("Bridge metadata capacity exceeded: " + key);
        }
        try
        {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException exception)
        {
            throw new IllegalArgumentException("Invalid bridge metadata UTF-8: " + key, exception);
        }
    }
}
