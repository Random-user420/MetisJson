/*
 Copyright 2025 Lilith

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package io.lilithtechs.metrisjson;


import java.util.ArrayList;
import java.util.List;


class JsonUtilsTest {
    // --- Helper classes for testing ---
    static class Person {
        private String name;
        private int age;
        private transient String city;
        private List<String> skills;
        private static final String SPECIES = "Human";


        public Person(String name, int age, String city, List<String> skills) {
            this.name = name;
            this.age = age;
            this.city = city;
            this.skills = skills;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
        public List<String> getSkills() { return skills; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Person person = (Person) o;
            return age == person.age &&
                    java.util.Objects.equals(name, person.name) &&
                    java.util.Objects.equals(skills, person.skills);
        }
    }

    static class Team {
        private String teamName;
        private List<Person> members;

        public Team() {}

        public Team(String teamName, List<Person> members) {
            this.teamName = teamName;
            this.members = members;
        }

        public String getTeamName() { return teamName; }
        public List<Person> getMembers() { return members; }
    }

    public void testSimpleObjectSerialization() throws Exception {
        Person person = new Person("John Doe", 30, "New York", List.of("Java", "Python"));
        String json = JsonUtils.toJson(person);

        assertCondition(json.contains("\"name\":\"John Doe\""), "JSON should contain name");
        assertCondition(json.contains("\"age\":30"), "JSON should contain age");
        assertCondition(json.contains("\"skills\":[\"Java\",\"Python\"]"), "JSON should contain skills");
        assertCondition(!json.contains("city"), "JSON should not contain transient field 'city'");
        assertCondition(!json.contains("SPECIES"), "JSON should not contain static field 'SPECIES'");
    }

    public void testSimpleObjectDeserialization() throws Exception {
        String json = "{\"name\":\"John Doe\",\"age\":30,\"skills\":[\"Java\",\"Python\"]}";
        Person person = JsonUtils.fromJson(json, Person.class);

        assertCondition(person.getName().equals("John Doe"), "Deserialized name should be 'John Doe'");
        assertCondition(person.getAge() == 30, "Deserialized age should be 30");
        assertCondition(person.getSkills().size() == 2, "Deserialized skills should have 2 items");
        assertCondition(person.getSkills().contains("Java"), "Skills should include 'Java'");
    }

    public void testTeamDeserializationWithNestedObjects() throws Exception {
        String teamJson = "{\"teamName\":\"Eagles\",\"members\":[{\"name\":\"Jane Smith\",\"age\":25,\"skills\":[\"C#\",\"JavaScript\"]},{\"name\":\"Peter Jones\",\"age\":42,\"skills\":[\"Go\",\"Rust\"]}]}";

        Team team = JsonUtils.fromJson(teamJson, Team.class);

        assertCondition(team.getTeamName().equals("Eagles"), "Team name should be 'Eagles'");
        assertCondition(team.getMembers().size() == 2, "Team should have 2 members");

        Person member1 = team.getMembers().get(0);
        assertCondition(member1.getName().equals("Jane Smith"), "Member 1 name is incorrect");
        assertCondition(member1.getAge() == 25, "Member 1 age is incorrect");

        Person member2 = team.getMembers().get(1);
        assertCondition(member2.getName().equals("Peter Jones"), "Member 2 name is incorrect");
        assertCondition(member2.getSkills().get(0).equals("Go"), "Member 2 skill is incorrect");
    }

    public void testFullSerializationCycle() throws Exception {
        List<Person> people = new ArrayList<>();
        people.add(new Person("Jane Smith", 25, "London", List.of("C#", "JavaScript")));
        people.add(new Person("Peter Jones", 42, "Paris", List.of("Go", "Rust")));
        Team originalTeam = new Team("Eagles", people);

        // Convert to JSON
        String teamJson = JsonUtils.toJson(originalTeam);

        // Convert back to an object
        Team deserializedTeam = JsonUtils.fromJson(teamJson, Team.class);

        assertCondition(originalTeam.getTeamName().equals(deserializedTeam.getTeamName()), "Team names should match after cycle");
        assertCondition(originalTeam.getMembers().equals(deserializedTeam.getMembers()), "Team members should be equal after cycle");
    }

    public void testNullValue() throws Exception {
        String json = JsonUtils.toJson(null);
        assertCondition("null".equals(json), "toJson(null) should return 'null'");

        Person p = JsonUtils.fromJson("null", Person.class);
        assertCondition(p == null, "fromJson('null') should return null");
    }

    /**
     * Helper assertion method. Throws an AssertionError if the condition is false.
     * @param condition The boolean condition to check.
     * @param message The message for the AssertionError if the condition is false.
     */
    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}