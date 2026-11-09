/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.util.io.BukkitObjectInputStream
 *  org.bukkit.util.io.BukkitObjectOutputStream
 */
package com.lucidaps.utilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class BinaryInventoryUtil {
    private BinaryInventoryUtil() {
    }

    public static byte[] serializeInventory(ItemStack[] itemStackArray) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream bukkitObjectOutputStream = new BukkitObjectOutputStream((OutputStream)byteArrayOutputStream);
        bukkitObjectOutputStream.writeInt(itemStackArray.length);
        for (ItemStack itemStack : itemStackArray) {
            bukkitObjectOutputStream.writeObject((Object)itemStack);
        }
        bukkitObjectOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    public static ItemStack[] deserializeInventory(byte[] byArray) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byArray);
        BukkitObjectInputStream bukkitObjectInputStream = new BukkitObjectInputStream((InputStream)byteArrayInputStream);
        int n = bukkitObjectInputStream.readInt();
        ItemStack[] itemStackArray = new ItemStack[n];
        for (int i = 0; i < n; ++i) {
            itemStackArray[i] = (ItemStack)bukkitObjectInputStream.readObject();
        }
        bukkitObjectInputStream.close();
        return itemStackArray;
    }

    public static byte[] serializeCompressed(ItemStack[] itemStackArray, JavaPlugin javaPlugin) {
        Object object;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            object = new BukkitObjectOutputStream((OutputStream)byteArrayOutputStream);
            try {
                object.writeInt(itemStackArray.length);
                for (ItemStack itemStack : itemStackArray) {
                    object.writeObject((Object)itemStack);
                }
            }
            finally {
                object.close();
            }
        }
        catch (Exception exception) {
            javaPlugin.getLogger().severe("Serialization failed: " + exception.getMessage());
            throw exception;
        }
        object = new Deflater(-1, true);
        ((Deflater)object).setInput(byteArrayOutputStream.toByteArray());
        ((Deflater)object).finish();
        byte[] byArray = new byte[8192];
        ByteArrayOutputStream byteArrayOutputStream2 = new ByteArrayOutputStream();
        while (!((Deflater)object).finished()) {
            int n = ((Deflater)object).deflate(byArray);
            byteArrayOutputStream2.write(byArray, 0, n);
        }
        ((Deflater)object).end();
        return byteArrayOutputStream2.toByteArray();
    }

    public static ItemStack[] deserializeCompressed(byte[] byArray) {
        Inflater inflater = new Inflater(true);
        inflater.setInput(byArray);
        byte[] byArray2 = new byte[32768];
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (!inflater.finished()) {
            int n = inflater.inflate(byArray2);
            byteArrayOutputStream.write(byArray2, 0, n);
        }
        inflater.end();
        try (BukkitObjectInputStream bukkitObjectInputStream = new BukkitObjectInputStream((InputStream)new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));){
            int n = bukkitObjectInputStream.readInt();
            ItemStack[] itemStackArray = new ItemStack[n];
            for (int i = 0; i < n; ++i) {
                itemStackArray[i] = (ItemStack)bukkitObjectInputStream.readObject();
            }
            ItemStack[] itemStackArray2 = itemStackArray;
            return itemStackArray2;
        }
    }
}

