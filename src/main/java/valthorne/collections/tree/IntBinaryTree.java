package valthorne.collections.tree;

import java.util.Arrays;

/**
 * A simple implementation of a binary tree that stores integers.
 * This implementation allows insertion and search operations on the binary tree.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class IntBinaryTree {

    private Node[] nodes = new Node[10]; // Array to store nodes of the binary tree
    private int size; // Current size of the binary tree

    /**
     * Inserts a new integer value into the binary tree.
     *
     * @param value the integer value to be inserted
     */
    public void insert(int value) {
        // Resize the nodes array if it's full
        if (size == nodes.length)
            nodes = Arrays.copyOf(nodes, (int) (nodes.length * 1.25));

        Node newNode = new Node(value); // Create a new node with the given value

        // If the binary tree is empty, set the new node as the root
        if (nodes[0] == null) {
            nodes[0] = newNode;
            return;
        }

        int currentIndex = 0; // Start from the root node
        while (true) {
            Node currentNode = nodes[currentIndex]; // Get the current node
            // Decide whether to move left or right based on the value of the current node
            if (currentNode.value < value) {
                if (currentNode.left == -1) {
                    currentNode.left = size; // Set the left child of the current node to the index of the new node
                    break;
                }
                currentIndex = currentNode.left; // Update the index to the left child
            } else {
                if (currentNode.right == -1) {
                    currentNode.right = size; // Set the right child of the current node to the index of the new node
                    break;
                }
                currentIndex = currentNode.right; // Update the index to the right child
            }
        }

        nodes[size++] = newNode; // Add the new node to the nodes array
    }

    /**
     * Searches for a given integer value in the binary tree.
     *
     * @param value the integer value to search for
     * @return true if the value is found, false otherwise
     */
    public boolean search(int value) {
        int currentIndex = 0; // Start from the root node
        while (currentIndex != -1) { // Continue searching until reaching a leaf node
            Node currentNode = nodes[currentIndex]; // Get the current node
            if (currentNode == null)
                return false; // Return false if the current node is null (not found)
            if (currentNode.value == value)
                return true; // Return true if the value is found
            // Decide whether to move left or right based on the value of the current node
            currentIndex = currentNode.value < value ? currentNode.left : currentNode.right;
        }
        return false; // Return false if the value is not found
    }

    /**
     * Returns a string representation of the binary tree.
     *
     * @return a string representation of the binary tree
     */
    @Override
    public String toString() {
        return Arrays.toString(nodes); // Return the string representation of the nodes array
    }

    /**
     * Inner class representing a node in the binary tree.
     */
    private static class Node {
        int value; // Value stored in the node
        int left = -1; // Index of the left child node in the nodes array
        int right = -1; // Index of the right child node in the nodes array

        /**
         * Constructs a new node with the given value.
         *
         * @param value the value to be stored in the node
         */
        public Node(int value) {
            this.value = value;
        }

        /**
         * Returns a string representation of the node.
         *
         * @return a string representation of the node
         */
        @Override
        public String toString() {
            return "Node[left=%s, right=%s, value=%s]".formatted(left, right, value);
        }
    }
}