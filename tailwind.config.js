/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/resources/templates/**/*.kte",
    "./src/main/resources/assets/**/*.js",
  ],
  theme: {
    extend: {},
  },
  daisyui: {
    themes: false,
  },
  plugins: [require("daisyui"), require("@tailwindcss/typography")],
};
