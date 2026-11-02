// What metro.transformer.js and jest.text-transform.js turn these imports into: the file's text.
declare module '*.yaml' {
  const text: string;
  export default text;
}

declare module '*.yml' {
  const text: string;
  export default text;
}

declare module '*.svg' {
  const text: string;
  export default text;
}
