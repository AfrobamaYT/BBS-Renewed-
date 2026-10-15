package net.minecraft.network.codec;

import com.mojang.datafixers.util.Function5;

import java.util.function.Function;

/** vor1205: Mojangs StreamCodec kam mit 1.20.5; hier nur, was BBS davon benutzt. */
public interface StreamCodec<B, V>
{
    V decode(B buf);

    void encode(B buf, V value);

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
        StreamCodec<? super B, T1> c1, Function<C, T1> g1,
        StreamCodec<? super B, T2> c2, Function<C, T2> g2,
        StreamCodec<? super B, T3> c3, Function<C, T3> g3,
        StreamCodec<? super B, T4> c4, Function<C, T4> g4,
        StreamCodec<? super B, T5> c5, Function<C, T5> g5,
        Function5<T1, T2, T3, T4, T5, C> bauen)
    {
        return new StreamCodec<>()
        {
            @Override
            public C decode(B buf)
            {
                return bauen.apply(c1.decode(buf), c2.decode(buf), c3.decode(buf), c4.decode(buf), c5.decode(buf));
            }

            @Override
            public void encode(B buf, C wert)
            {
                c1.encode(buf, g1.apply(wert));
                c2.encode(buf, g2.apply(wert));
                c3.encode(buf, g3.apply(wert));
                c4.encode(buf, g4.apply(wert));
                c5.encode(buf, g5.apply(wert));
            }
        };
    }
}
