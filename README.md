# PAP_26L_Projekt_harmonogram_opracji

## Ogólny opis

Projekt skupia się na aplikacji webowej do tworzenia i edytowania harmonogramów (wykresów Gantta).

## Funkcjonalności

- wygodne przeglądanie harmongramów
- prosta edycja harmonogramów
- łatwy import i export danych
- tworzenie operacji i możliwość nadawania im atrybutów
- tworzenie wykresów wymagań i zasobów
- automatyczne podliczenie kosztów finasowych i czasowych

## Instalacja

1. Sklonuj lub pobierz pliki projektu

```bash
git clone https://gitlab-stud.elka.pw.edu.pl/mwrobel3/PAP_26L_Projekt_harmonogram_opracji
```

## Aspekty techniczne projektu

tworzymy aplikację w 3 wartowej strukturze podzielonej następująco:
 - logika interfejsu użytkownika
 - logika biznesowa
 - baza danych

Aplikacja jest budowana głownie w javie z wykorzystaniem React oraz Spring. Baza danych jest zaimplementowana w CSV (SQL), aczkolwiek jesteśmy otwarci na sugestie co do wyboru bazy danych.

## Przyszły rozwój
- automatyczna optymalizacja harmonogramów