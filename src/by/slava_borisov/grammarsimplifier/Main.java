package by.slava_borisov.grammarsimplifier;

import java.util.*;
import java.util.regex.*;

public class Main {
    static final String EPS = "ε";
    static final Pattern SYMBOL = Pattern.compile(":=|[A-Z][0-9]*|[a-z]+|[^\\s]");

    static class Grammar {
        String start;
        LinkedHashMap<String, LinkedHashSet<List<String>>> rules = new LinkedHashMap<>();

        Grammar(String start) {
            this.start = start;
        }

        void add(String left, List<String> right) { //добавление правил без повторок
            rules.computeIfAbsent(left, k -> new LinkedHashSet<>()).add(List.copyOf(right));
        }

        void read(String line) {
            String[] sides = line.trim().split("\\s*(?:->|→)\\s*", 2);

            if (sides.length != 2 || !sides[0].matches("[A-Z][0-9]*")) {
                throw new IllegalArgumentException("Неверное правило: " + line);
            }

            for (String variant : sides[1].split("\\|", -1)) { //разделяем правую часть
                variant = variant.trim();

                if (variant.isEmpty()) {
                    throw new IllegalArgumentException("Пустая альтернатива: " + line);
                }

                if (variant.equals(EPS)) { //эпсилон?? добавляем как пустую правую часть
                    add(sides[0], List.of());
                    continue;
                }

                Matcher matcher = SYMBOL.matcher(variant); //ищем отдельные части из правой части
                List<String> right = new ArrayList<>();

                while (matcher.find()) { //ищем символы в правой части пока не закончатся
                    String token = matcher.group();

                    if (token.equals(EPS)) {
                        throw new IllegalArgumentException("ε должна стоять отдельно");
                    }

                    right.add(token);
                }

                add(sides[0], right); //добавляем правое правило в грамматику
            }
        }

        boolean isUnit(List<String> right) { //цепное ?????
            return right.size() == 1 && rules.containsKey(right.get(0));
        }

        Grammar removeEmpty() {
            Set<String> nullable = new LinkedHashSet<>();

            for (var entry : rules.entrySet()) {
                if (entry.getValue().contains(List.of())) { //есть пустое правило??
                    nullable.add(entry.getKey()); //добавляем такой нетерминал
                }
            }

            System.out.println("W0 = " + nullable);

            int iteration = 1;

            while (true) {
                Set<String> next = new LinkedHashSet<>(nullable);

                for (var entry : rules.entrySet()) {
                    for (List<String> right : entry.getValue()) {
                        if (right.stream().allMatch(nullable::contains)) { //могут ли все нетерминалы попасть в набор
                            next.add(entry.getKey()); //тогда S в набор добавим
                        }
                    }
                }

                System.out.println("W" + iteration++ + " = " + next);

                if (next.equals(nullable)) {
                    break;
                }

                nullable = next;
            }

            Grammar result = new Grammar(start);

            for (String left : rules.keySet()) { //переносим нетерминалы в грамматику без правил
                result.rules.put(left, new LinkedHashSet<>());
            }
            //перебираем старые правила и создаем новые с удал-м тех которые можно удалить
            for (var entry : rules.entrySet()) {
                for (List<String> right : entry.getValue()) { //варианты правой части
                    if (!right.isEmpty()) {
                        expand( //возможные варианты правила с удалением тех, которые могут изчезнуть
                                result,
                                entry.getKey(),
                                right,
                                0,
                                new ArrayList<>(),
                                nullable
                        );
                    }
                }
            }

            if (nullable.contains(start)) { //если S дает эпсилон
                String newStart = "S0";
                int number = 0;

                while (rules.containsKey(newStart) || appearsInRight(newStart)) {
                    newStart = "S" + ++number;
                }

                //переставляем правила
                LinkedHashMap<String, LinkedHashSet<List<String>>> reordered =
                        new LinkedHashMap<>();

                reordered.put(newStart, new LinkedHashSet<>());
                reordered.putAll(result.rules);

                result.rules = reordered;
                result.start = newStart;

                result.add(newStart, List.of(start));
                result.add(newStart, List.of());

                System.out.println(
                        "Начальный символ порождает ε. Добавлен "
                                + newStart + " → " + start + " | ε"
                );
            }

            return result;
        }

        //используется ли символ в правых частях правил грамматики
        boolean appearsInRight(String symbol) {
            for (var variants : rules.values()) {
                for (List<String> right : variants) {
                    if (right.contains(symbol)) {
                        return true;
                    }
                }
            }

            return false;
        }

        // Создаём разные варианты правил, удаляя символы, которые могут исчезнуть (ε)
        void expand(
                Grammar result,
                String left,
                List<String> right,
                int index,
                List<String> current,
                Set<String> nullable
        ) {
            // Проверяем, обработали ли все символы правой части
            if (index == right.size()) {
                // Если вариант не пустой и не ведёт символ на самого себя
                if (!current.isEmpty()
                        && !(current.size() == 1 && current.get(0).equals(left))) {
                    result.add(left, current);
                }
                return;
            }

            // Берём текущий символ правой части
            String symbol = right.get(index);
            current.add(symbol);

            // Переходим к обработке следующего символа
            expand(result, left, right, index + 1, current, nullable);

            // Убираем символ, чтобы попробовать другой вариант
            current.remove(current.size() - 1);

            // Если символ может исчезнуть, пробуем вариант без него
            if (nullable.contains(symbol)) {
                expand(result, left, right, index + 1, current, nullable);
            }
        }


        /// Устраняем цепные правила вида A → B
        Grammar removeUnit() {
            Grammar result = new Grammar(start);

            // Создаём пустые наборы правил для всех нетерминалов
            for (String left : rules.keySet()) {
                result.rules.put(left, new LinkedHashSet<>());
            }

            for (String left : rules.keySet()) {
                Set<String> reachable = new LinkedHashSet<>();
                Queue<String> queue = new ArrayDeque<>();

                reachable.add(left);
                queue.add(left);

                // Ищем все нетерминалы, связанные цепными правилами
                while (!queue.isEmpty()) {
                    String current = queue.remove(); //берем и удаляем

                    for (List<String> right : rules.get(current)) { //перебираем его правила
                        if (isUnit(right) && reachable.add(right.get(0))) { //проверяем цепное
                            queue.add(right.get(0));
                        }
                    }
                }

                // Выводим найденные цепные связи
                Set<String> chain = new LinkedHashSet<>(reachable);
                chain.remove(left); //из наолра удаляем исх нетерм

                System.out.println("N(" + left + ") = " + chain);

                // Перебираем все найденные связанные нетерминалы
                for (String symbol : reachable) {
                    // Перебираем правила каждого нетерминала
                    for (List<String> right : rules.get(symbol)) {
                        if (!isUnit(right)) { //цепные пропуск
                            result.add(left, right);
                        }
                    }
                }
            }

            return result;
        }

        void print() {
            System.out.println("Начальный символ: " + start);

            for (var entry : rules.entrySet()) {
                List<String> variants = new ArrayList<>();

                //Подготавливаем каждый вариант правой части
                for (List<String> right : entry.getValue()) {
                    variants.add(
                            right.isEmpty() ? EPS : String.join(" ", right)
                    );
                }

                System.out.println(
                        entry.getKey() + " → " + String.join(" | ", variants)
                );
            }
        }
    }

    static int readCount(Scanner scanner) {
        System.out.print("Количество строк с правилами: ");

        int count = Integer.parseInt(scanner.nextLine().trim());

        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество должно быть положительным"
            );
        }

        return count;
    }

    static Grammar inputGrammar(Scanner scanner) {
        int count = readCount(scanner);

        System.out.println(
                "Введите правила (например, S -> AB | a | ε):"
        );

        Grammar grammar = null;

        for (int i = 0; i < count; i++) {
            String line = scanner.nextLine().trim();

            if (grammar == null) {
                String[] sides = line.split(
                        "\\s*(?:->|→)\\s*", 2
                );

                if (sides.length != 2
                        || !sides[0].matches("[A-Z][0-9]*")) {
                    throw new IllegalArgumentException(
                            "Неверное первое правило"
                    );
                }

                grammar = new Grammar(sides[0]);
            }

            grammar.read(line);
        }

        for (var entry : grammar.rules.entrySet()) {
            for (List<String> right : entry.getValue()) {
                for (String symbol : right) {
                    if (symbol.matches("[A-Z][0-9]*")
                            && !grammar.rules.containsKey(symbol)) {
                        throw new IllegalArgumentException(
                                "Нет правил для нетерминала " + symbol
                        );
                    }
                }
            }
        }

        return grammar;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== ПРИВЕДЕНИЕ ГРАММАТИК ===");
            System.out.println("1 — устранение пустых правил");
            System.out.println("2 — устранение цепных правил");
            System.out.println("0 — выход");
            System.out.print("Выбор: ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String choice = scanner.nextLine().trim();

            if (choice.equals("0")) {
                System.out.println("Работа завершена.");
                break;
            }

            if (!choice.equals("1") && !choice.equals("2")) {
                System.out.println("Неверный пункт меню.");
                continue;
            }

            try {
                Grammar grammar = inputGrammar(scanner);

                System.out.println("\nИсходная грамматика:");
                grammar.print();

                if (choice.equals("1")) {
                    System.out.println("\nУстранение пустых правил:");
                    grammar = grammar.removeEmpty();
                } else {
                    System.out.println("\nУстранение цепных правил:");
                    grammar = grammar.removeUnit();
                }

                System.out.println("\nПолученная грамматика:");
                grammar.print();

            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: " + e.getMessage());
            } catch (NoSuchElementException e) {
                break;
            }
        }
    }
}