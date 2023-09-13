package com.droidlogic.settings;

import android.util.Log;

import java.util.Calendar;
import java.util.TimeZone;

public class CountryTimeZone {
    String country[] = {
        "arg","aut","bel","bol","bra",
        "chl","chn","col","cri","hrv",
        "cze","dom","ecu","slv","fin",
        "fra","deu","gtm","rou","hnd",
        "hun","ita","jpn","lva","lux",
        "mex","nic","nld","pan","per",
        "pol","prt","rus","srb","svk",
        "svn","esp","swe","che","twn",
        "gbr","ukr","ven","dnk","nor",
        "irl","vnm","tur","blr","aze",
        "irn","mar","tun","are","isr",
        "dza","jor","lby","irq","geo",
        "sau","sgp","mys","idn","tha",
        "ind","pak","phi","phl","guf",
        "tto", "pry","ury","cst",
    };

    String timezone[][] = {
        {"America/Argentina/Buenos_Aires"}, //arg
        {"Europe/Vienna"}, //aut
        {"Europe/Brussels"}, //bel
        {"America/La_Paz"}, //bol
        {
            "America/Noronha",
            "America/Sao_Paulo",
            "America/Araguaina",
            "America/Bahia",
            "America/Belem",
            "America/Recife",
            "America/Maceio",
            "America/Cuiaba",
            "America/Manaus",
            "America/Porto_Velho",
            "America/Boa_Vista",
            "America/Rio_Branco",
            "America/Eirunepe",}, //bra
        {"America/Santiago"}, //chi
        {"Asia/Shanghai"}, //chn
        {"America/Bogota"}, //col
        {"America/Costa_Rica"}, //cri
        {"Europe/Zagreb"}, //hrv
        {"Europe/Prague"}, //cze
        {"America/Santo_Domingo"}, //dom
        {"America/Guayaquil"}, //ecu
        {"America/El_Salvador"}, //slv
        {"Europe/Helsinki"}, //fin
        {"Europe/Paris"}, //fra
        {"Europe/Berlin"}, //deu
        {"America/Guatemala"}, //gtm
        {"Europe/Bucharest"}, //rou
        {"America/Tegucigalpa"}, //hnd
        {"Europe/Budapest"}, //hun
        {"Europe/Rome"}, //ita
        {"Asia/Tokyo"}, //jpn
        {"Europe/Riga"}, //lva
        {"Europe/Luxembourg"}, //lux
        {
            "America/Cancun",
            "America/Mexico_City",
            "America/Chihuahua",
            "America/Tijuana",
        },  //mex
        {"America/Managua"}, //nic
        {"Europe/Amsterdam"}, //nld
        {"America/Panama"}, //pan
        {"America/Lima"}, //per
        {"Europe/Warsaw"}, //pol
        {"Europe/Lisbon"}, //prt
        {
            "Asia/Kamchatka",
            "Asia/Magadan",
            "Asia/Vladivostok",
            "Asia/Yakutsk",
            "Asia/Irkutsk",
            "Asia/Krasnoyarsk",
            "Asia/Omsk",
            "Asia/Yekaterinburg",
            "Europe/Samara",
            "Europe/Moscow",
            "Europe/Kaliningrad",
        },  //rus
        {"Europe/Belgrade"}, //srb
        {"Europe/Bratislava"}, //svk
        {"Europe/Ljubljana"}, //svn
        {"Europe/Madrid"}, //esp
        {"Europe/Stockholm"}, //swe
        {"Europe/Zurich"}, //che
        {"Asia/Taipei"}, //twn
        {"Europe/London"}, //gbr
        {"Europe/Kiev"}, //ukr
        {"America/Caracas"}, //ven
        {"Europe/Copenhagen"}, //dnk
        {"Europe/Oslo"}, //nor
        {"Europe/Dublin"}, //irl
        {"Asia/Ho_Chi_Minh"}, //vnm
        {"Europe/Istanbul"}, //tur
        {"Europe/Minsk"}, //blr
        {"Asia/Baku"}, //aze
        {"Asia/Tehran"}, //irn
        {"Africa/Casablanca"}, //mar
        {"Africa/Tunis"}, //tun
        {"Asia/Dubai"}, //are
        {"Asia/Jerusalem"}, //isr
        {"Africa/Algiers"}, //dza
        {"Asia/Amman"}, //jor
        {"Africa/Tripoli"}, //lby
        {"Asia/Baghdad"}, //irq
        {"Asia/Tbilisi"}, //geo
        {"Asia/Riyadh"}, //sau
        {"Asia/Singapore"}, //sgp
        {"Asia/Kuching"}, //mys
        {
            "Asia/Jayapura",
            "Asia/Makassar",
            "Asia/Jakarta",
            "Asia/Pontianak",
        }, //idn
        {"Asia/Bangkok"}, //tha
        {"Asia/Kolkata"}, //ind
        {"Asia/Karachi"}, //pak
        {"Asia/Manila"}, //phi
        {"Asia/Manila"}, //phl
        {"America/Cayenne"}, //guf
        {"America/Port_of_Spain"}, //tto
        {"America/Asuncion"}, //pry
        {"America/Montevideo"}, //ury
        {"America/Managua"}, //cst
};

    public String[] getTimezone(String name){
        int i ;

        if (name != null) {
            for (i=0; i<country.length; i++) {
                if (name.equals(country[i])) {
                    return timezone[i];
                }
            }
        }

        return null;
    }

    public String print(){
        int i, j;

        for (i=0; i<timezone.length; i++) {
            for (j=0; j<timezone[i].length; j++) {
                TimeZone tz = TimeZone.getTimeZone(timezone[i][j]);
                Calendar now = Calendar.getInstance();
                now.setTimeZone(tz);
                int zoneOffset = now.get(java.util.Calendar.ZONE_OFFSET);
                zoneOffset /= 60000; //m
                int h = zoneOffset / 60;
                int m = zoneOffset % 60;

                String gmt;
                String str="";
                if (h < 0) {
                    str = "-";
                }
                else if (h > 0) {
                    str += "+";
                }
                gmt = String.format("GMT%s%02d:%02d", str, h, m);
            }
        }

        return null;
    }
}
